package org.example;

import java.util.HashMap;
import java.util.Map;

/**
 * Main class for elevator system
 */
public class ElevatorManager {
    private int m_floors;
    private int m_groupSize;  // how many elevators are in this bank
    private ElevatorDispatcher m_dispatcher;
    private Map<Integer, ElevatorController> m_elevators; // Map<groupId, elevator>

    /**
     * Default constructor
     */
    public ElevatorManager() {
        this(
                Integer.parseInt(System.getProperty("elevator.floors", "5")),
                Integer.parseInt(System.getProperty("elevator.size", "1"))
        );
    }

    /**
     * Construct a system of n elevators and m floors
     * @param floors
     * @param
     */
    public ElevatorManager(int floors, int groupSize) {
        assert(floors > 0);
        assert(groupSize > 0);

        m_floors = floors;
        m_groupSize = groupSize;

        m_dispatcher = new ElevatorDispatcher(this);

        m_elevators = new HashMap<>();
        for (int i = 0; i < m_groupSize; i++) {
            m_elevators.put(i, new ElevatorController(i));
        }
    }

    /**
     * Get the elevator dispatch (call button)
     * @return the dispatcher
     */
    public ElevatorDispatcher getDispatcher() {
        return m_dispatcher;
    }

    /**
     * Set system maintenance on or off; allow finishing current moves then stop
     * @param elevatorId  elevator to be shut down\
     * @param on  whether to shut down or restart elevator
     * @return current maintenance mode
     */
    public boolean setMaintenance(int elevatorId, boolean on) {
        ElevatorController elevator = m_elevators.get(elevatorId);
        if (elevator != null) {
            return elevator.setMaintenance(on);
        } else {
            System.out.println("setMaintenance: no such elevator with Id: " + elevatorId);
            return false;
        }
    }

    /**
     * Indicates whether the system is in maintenance mode
     * @return current maintenance mode
     */
    public boolean isMaintenance(int elevatorId) {
        ElevatorController elevator = m_elevators.get(elevatorId);
        if (elevator != null) {
            return elevator.isMaintenance();
        } else {
            System.out.println("isMaintenance: no such elevator with Id: " + elevatorId);
            return false;
        }
    }

    /**
     * Get a reference on the internal map of elevators
     * @return elevator map
     */
    protected Map<Integer, ElevatorController> getElevators() {
        return m_elevators;
    }

    /**
     * Get configured number of floors
     * @return number of floors
     */
    protected int getFloors() {
        return m_floors;
    }
}
