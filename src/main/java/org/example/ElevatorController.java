package org.example;

import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 *  Elevator controls
 */
public class ElevatorController {
    private int m_groupId;
    private int m_currentFloor = 1;
    private boolean m_emergency = false;   // these two are boolean so as to allow state changes still
    private boolean m_maintenance = false;
    private TreeSet<Integer> m_selectedFloors = new TreeSet<>(); // ordered selection
    private ElevatorState m_state;
    private final ExecutorService m_executor = Executors.newSingleThreadExecutor();
    private final WorkerThread m_worker = new WorkerThread();

    /**
     * Main constructor
     * @param groupId  Id of elevator when more than 1 in the bank
     */
    public ElevatorController(int groupId) {
        m_groupId = groupId;
        m_state = ElevatorState.IDLE;
        m_worker.start();
    }

    /**
     * Buttons for selecting floors, inside elevator cabin
     * Synchronize "selected floors" access to prevent undesirable side effects of mashing several buttons at once
     * @param floor  desired floor number
     */
    public void selectFloor(int floor) {
        System.out.println("Pressed button for floor: " + floor);
        if (isMaintenance()) {
            System.out.println("Selection " + floor + " ignored, system in maintenance mode");
            return;
        }
        synchronized (this) {
            m_selectedFloors.add(floor);
        }
        System.out.println("Wake up worker");
        synchronized (m_worker) {
            m_worker.notify(); // wake up worker
        }
    }

    /**
     * Floor number setter, should not be accessible externally
     * @param floor  floor value
     */
    private void setCurrentFloor(int floor) {
        m_currentFloor = floor;
    }

    /**
     * Floor number getter, could also be used to display floor where
     * this elevator is above doors for example
     * @return floor where elevator is situated
     */
    public int getCurrentFloor() {
        return m_currentFloor;
    }

    /**
     * Elevator group Id getter
     * @return group Id
     */
    protected int getGroupId() {
        return m_groupId;
    }

    /**
     * Put elevator in emergency mode; only emergency responders allowed
     */
    public void emergencyOverride() {
        m_emergency = true;
    }

    /**
     * Take elevator out of emergency mode
     */
    public void disableEmergency() {
        m_emergency = false;
    }

    /**
     * Set elevator into or out of maintenance mode
     * @param mode  true for maintenance, false for normal operation
     * @return returns new mode, ensures this was executed
     */
    public boolean setMaintenance(boolean mode) {
        return m_maintenance = mode;
    }

    /**
     * Gets elevator maintenance mode
     * @return true for maintenance, false for normal operation
     */
    public boolean isMaintenance() {
        return m_maintenance;
    }

    /**
     * Manage elevator state
     * @param state  state value
     */
    protected void setState(ElevatorState state) {
        m_state = state;
    }

    /**
     * Elevator state getter
     * @return
     */
    protected ElevatorState getState() {
        return m_state;
    }

    /**
     * After being called, this reserves the elevator and brings it to the desired floor
     * @param targetFloor  desired floor
     * @param directionUp  direction chosen by user
     * @return  future for an elevator, or null if not possible (e.g.: emergency mode)
     */
    public Future<ElevatorController> reserve(int targetFloor, boolean directionUp) {
        if (m_emergency) {
            // can only be placed in emergency state after called once
            // after that, all attempts will to call will fail
            return null;
        }

        this.setState(directionUp ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN); // direction does not matter

        if (m_currentFloor == targetFloor) {
            // no change of state
            return m_executor.submit(() -> this);
        }

        System.out.println("Elevator " + m_groupId + " floor: " + m_currentFloor + " target floor: " + targetFloor);

        // bring to target floor
        return m_executor.submit(() -> {
            // set planned direction, elevator was called for one and only one
            // if another direction is pressed, another elevator will receive it
            // if only one elevator in group then first come first serve
            this.setState(directionUp ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN);
            goToFloor(targetFloor);
            return this;
        });
    }

    /**
     * Internal method to perform elevator movement
     * @param targetFloor
     */
    private void goToFloor(int targetFloor) {
        try {
            while (targetFloor != m_currentFloor) {
                System.out.println("Elevator " + m_groupId + " current floor: " + m_currentFloor);
                Thread.sleep(2_000L); // time to move

                if (targetFloor < m_currentFloor) {
                    setCurrentFloor(m_currentFloor - 1);
                }

                if (targetFloor > m_currentFloor) {
                    setCurrentFloor(m_currentFloor + 1);
                }
            }
        } catch (InterruptedException ie) {
            System.out.println("Elevator interrupted");
        }
        System.out.println("Elevator, done " + m_groupId + " current floor: " + m_currentFloor);
    }

    /**
     * All possible elevator states
     */
    protected enum ElevatorState {
        IDLE,        // available
        MOVING_UP,   // for up travel
        MOVING_DOWN, // for down travel
        SHUTDOWN
    }

    /**
     * Helper method to find closest floor
     */
    private int selectClosest() {
        Integer closest = null;
        if (getState() == ElevatorState.MOVING_UP) {
            closest = m_selectedFloors.higher(getCurrentFloor());
            if (closest == null) {
                // either: up was picked, but only lower floors selected
                // or: lower floors were pressed and we reverse direction
                setState(ElevatorState.MOVING_DOWN);
                closest = m_selectedFloors.lower(getCurrentFloor());
            }
            System.out.println("selectClosest moving up to " + closest + " from " + getCurrentFloor());
        } else if (getState() == ElevatorState.MOVING_DOWN) {
            closest = m_selectedFloors.lower(getCurrentFloor());
            System.out.println("selectClosest(0) moving down to " + closest + " from " + getCurrentFloor());
            if (closest == null) {
                // either: up was picked, but only lower floors selected
                // or: lower floors were pressed and we reverse direction
                setState(ElevatorState.MOVING_UP);
                closest = m_selectedFloors.higher(getCurrentFloor());
            }
            System.out.println("selectClosest moving down to " + closest + " from " + getCurrentFloor());
            return closest == null ? getCurrentFloor() : closest;
        } else if (getState() == ElevatorState.IDLE) { // elevator already here
            if (m_selectedFloors.higher(getCurrentFloor()) == null) {
                closest = m_selectedFloors.lower(getCurrentFloor());
            } else if (m_selectedFloors.lower(getCurrentFloor()) == null) {
                closest = m_selectedFloors.higher(getCurrentFloor());
            } else if (getCurrentFloor() - m_selectedFloors.lower(getCurrentFloor()) >
                       m_selectedFloors.higher(getCurrentFloor()) - getCurrentFloor()) { // neither are null
                closest = m_selectedFloors.higher(getCurrentFloor());
            } else {
                closest = m_selectedFloors.lower(getCurrentFloor());
            }
        }
        return closest == null ? getCurrentFloor() : closest;
    }

    /**
     * Helper class that controls the elevator movements
     */
    class WorkerThread extends Thread {
        public void run() {
            // Possible enhancement: park elevators at strategic locations after some time
            while(true) {
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException ie) {
                    // ignored
                }

                // time for doors to close
                try {
                    Thread.sleep(500); // yes, they're fast doors...
                } catch (InterruptedException ignored) {
                    // ignore, if selection was made let it complete
                }

                int prevSelected = 0;
                while (!m_selectedFloors.isEmpty()) { // safe to exhaust selections, this elevator reverses course
                    int selected = selectClosest(); // select closest, determine direction
                    if (prevSelected == selected) {
                        // avoid infinite loops, closest can return a non-selected floor
                        System.out.println("ERROR for selected floor: " + selected + " state: " + m_state);
                        break;
                    }
                    System.out.println("Got selected floor: " + selected + " state: " + m_state);

                    goToFloor(selected);

                    m_selectedFloors.remove(selected);
                    prevSelected = selected;
                }
                setState(ElevatorState.IDLE);
                System.out.println("No more floors, go to sleep");
            }
        }
    }
}
