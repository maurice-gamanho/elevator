package org.example;

import java.util.Map;
import java.util.concurrent.Future;

import static java.lang.Math.abs;

/**
 * Abstraction of the "call" button
 * There is one on each floor, and the intended direction must be specified
 */
public class ElevatorDispatcher {
    private ElevatorManager m_manager;

    /**
     * Main constructor
     * @param system  the elevator manager that this dispatcher belongs to
     */
    public ElevatorDispatcher(ElevatorManager system) {
        m_manager = system;
    }

    /**
     * Call an elevator; the floor where the button is pressed and the direction wanted
     * are parameter
     * Prevent race conditions by synchronizing
     * @param thisFloor  floor where button is located
     * @param directionUp  direction (usually up/down buttons)
     * @return  a future of elevator controls; they can only be used when the elevator has arrived
     *          null if the system is under maintenance
     */
    public synchronized Future<ElevatorController> call(int thisFloor, boolean directionUp) {
        Map<Integer, ElevatorController> elevators = m_manager.getElevators();

        ElevatorController c = null;
        if (elevators.size() == 1) {
            c = elevators.get(0);
            if (c.isMaintenance()) {
                c = null;
            }
        } else {
            int smallestGap = m_manager.getFloors();
            // Possible enhancement: randomize if more than 1 are at same gap
            // to avoid wear/tear on a single elevator
            for (Map.Entry<Integer, ElevatorController> e : elevators.entrySet()) {
                if (e.getValue().isMaintenance()) {
                    continue;
                }

                int gap = abs(thisFloor - e.getValue().getCurrentFloor());
                if (gap <= smallestGap && e.getValue().getState() == ElevatorController.ElevatorState.IDLE) {
                    c = e.getValue();
                }
                smallestGap = gap;
            }
        }

        return c == null ? null : c.reserve(thisFloor, directionUp);
    }
}
