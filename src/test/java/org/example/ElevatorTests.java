package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests exercise elevator and ancillary functions (emergency, maintenance)
 */
@DisplayName("Elevator tests")
public class ElevatorTests {
    /**
     * Test defaults and system properties
     */
    @Test
    @DisplayName("Test properties")
    public void propertiesTest() {
        System.setProperty("elevator.floors", "xyz");
        assertThrows(NumberFormatException.class, ElevatorManager::new);
        System.setProperty("elevator.size", "xyz");
        assertThrows(NumberFormatException.class, ElevatorManager::new);

        System.clearProperty("elevator.floors");
        System.clearProperty("elevator.size");
        ElevatorManager manager = new ElevatorManager();
        assertEquals(manager.getFloors(), 5);
    }

    /**
     * Basic test
     */
    @Test
    @DisplayName("Basic test")
    public void basicTest() throws Exception {
        ElevatorManager s = new ElevatorManager();

        ElevatorController c = s.getDispatcher().call(3, true).get();
        c.selectFloor(4);

        Thread.sleep(3_000L); // need some time for elevator to get there

        assertEquals(4, c.getCurrentFloor());
    }

    /**
     * Multi elevator test
     */
    @Test
    @DisplayName("Basic test, multi-elevator")
    public void multiElevatorBasicTest() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 3);

        ElevatorController c = s.getDispatcher().call(3, true).get(); // this waits
        System.out.println("Elevator selected: " + c.getGroupId());
        c.selectFloor(4);

        Thread.sleep(4_000); // time to move 1 floor plus extra

        assertEquals(4, c.getCurrentFloor());
    }

    /**
     * Simulate 2 people calling same elevator
     */
    @Test
    @DisplayName("Multi-elevator, multi-dispatch test")
    public void shouldNotBeSameElevatorTest() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 5);

        Future<ElevatorController> c1 = s.getDispatcher().call(3, true);
        Future<ElevatorController> c2 = s.getDispatcher().call(5, true);

        // wait for both to arrive
        while (!c1.isDone() && !c2.isDone()) {
            Thread.sleep(200);
        }

        assertNotNull(c1);
        assertNotNull(c2);

        // after being called, elevator stays in selected direction
        assertNotEquals(ElevatorController.ElevatorState.IDLE, c1.get().getState());
        assertNotEquals(ElevatorController.ElevatorState.IDLE, c2.get().getState());

        assertNotEquals(c1.get().getGroupId(), c2.get().getGroupId());
    }

    /**
     * Button-mashing test
     */
    @Test
    @DisplayName("Single elevator multi selection test")
    public void singleElevatorMultiCallsTest() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 1);

        ElevatorController c = s.getDispatcher().call(1, true).get(); // this waits
        c.selectFloor(4);
        c.selectFloor(5);
        c.selectFloor(6);

        Thread.sleep(14_000); // time to move to last floor plus extra

        assertEquals(c.getCurrentFloor(), 6);
    }

    /**
     * Go down first, then honor selections; this is a choice, other implementations
     * could just ignore the change in direction
     */
    @Test
    @DisplayName("Single elevator multi dispatch test")
    public void singleElevatorMultiCallsUpDownTest() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 1);

        ElevatorController c = s.getDispatcher().call(5, false).get(); // this waits
        c.selectFloor(4);
        c.selectFloor(5);
        c.selectFloor(6);

        Thread.sleep(3_000L);
        assertEquals(c.getCurrentFloor(), 4);

        Thread.sleep(10_000); // time to move to last floor plus extra

        assertEquals(c.getCurrentFloor(), 6);
    }

    /**
     * Start higher than selection
     */
    @Test
    @DisplayName("Single elevator multi dispatch and selections in reverse test")
    public void singleElevatorMultiCallsReverseTest() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 1);

        ElevatorController c = s.getDispatcher().call(7, false).get(); // this waits
        c.selectFloor(3);
        c.selectFloor(4);
        c.selectFloor(5);

        Thread.sleep(5_000L);
        assertEquals(c.getCurrentFloor(), 5);

        Thread.sleep(10_000); // time to move to last floor plus extra

        assertEquals(c.getCurrentFloor(), 3);
    }

    /**
     * Test that a selection can only be made on an IDLE elevator
     */
    @Test
    @DisplayName("Concurrent selections test")
    public void concurrentSelectionTest() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 1);

        // two simultaneous calls, single elevator
        Future<ElevatorController> c1 = s.getDispatcher().call(5, false);
        Future<ElevatorController> c2 = s.getDispatcher().call(4, true);

        // wait concurrently, closest should win
        Runnable pass1 = () -> {
            try {
                while (!c1.isDone()) {
                    Thread.sleep(100);
                }
                c1.get().selectFloor(1);
            } catch (Exception ignored) {
            }
        };
        Runnable pass2 = () -> {
            try {
                while (!c2.isDone()) {
                    Thread.sleep(100);
                }
                c2.get().selectFloor(9);
            } catch (Exception ignored) {
            }
        };
        new Thread(pass1).start();
        new Thread(pass2).start();

        Thread.sleep(35_000L);

        assertEquals(c2.get().getCurrentFloor(), 9);
    }

    /**
     * Emergency override test
     */
    @Test
    @DisplayName("Emergency override test")
    public void emergencyOverrideTest() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 1);

        ElevatorController c = s.getDispatcher().call(1, true).get(); // this waits

        c.emergencyOverride();
        c.selectFloor(5);

        // ensure elevator cannot be called while in emergency
        Future<ElevatorController> cNormal = s.getDispatcher().call(1, true);
        assertNull(cNormal);

        Thread.sleep(10_000L);
        assertEquals(c.getCurrentFloor(), 5);
        c.disableEmergency();

        cNormal = s.getDispatcher().call(1, true);
        cNormal.get().selectFloor(3);

        Thread.sleep(10_000L);
        assertEquals(cNormal.get().getCurrentFloor(), 3);
    }

    /**
     * System maintenance
     */
    @Test
    @DisplayName("System maintenance test 1")
    public void systemMaintenanceTest1() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 1);

        ElevatorController c = s.getDispatcher().call(1, true).get(); // this waits
        c.selectFloor(5);

        s.setMaintenance(0, true); // allow current operations to finish

        c.selectFloor(3); // new selections are ignored
        Thread.sleep(11_000L);
        assertEquals(c.getCurrentFloor(), 5);

        // ensure elevator cannot be called while in maintenance
        Future<ElevatorController> cNormal = s.getDispatcher().call(1, true);
        assertNull(cNormal);

        Thread.sleep(10_000L);
        assertEquals(c.getCurrentFloor(), 5);

        s.setMaintenance(0, false);

        cNormal = s.getDispatcher().call(1, true);
        cNormal.get().selectFloor(3);

        Thread.sleep(10_000L);
        assertEquals(cNormal.get().getCurrentFloor(), 3);
    }

    /**
     * System maintenance, only 1 elevator is down so another can be called
     */
    @Test
    @DisplayName("System maintenance test 2")
    public void systemMaintenanceTest2() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 2);

        ElevatorController c = s.getDispatcher().call(1, true).get(); // this waits
        c.selectFloor(5);

        s.setMaintenance(c.getGroupId(), true); // allow current operations to finish

        c.selectFloor(3); // new selections are ignored
        Thread.sleep(11_000L);
        assertEquals(c.getCurrentFloor(), 5);

        // ensure another elevator can still be called while first is in maintenance
        Future<ElevatorController> cNormal = s.getDispatcher().call(1, true);
        assertNotNull(cNormal);

        Thread.sleep(1_000L);
        assertEquals(c.getCurrentFloor(), 5);

        cNormal.get().selectFloor(3);

        Thread.sleep(7_000L);
        assertEquals(cNormal.get().getCurrentFloor(), 3);

        s.setMaintenance(0, false);
    }

    /**
     * System maintenance, stop and restart elevator worker thread
     */
    @Test
    @DisplayName("System maintenance test 3")
    public void systemMaintenanceTest3() throws Exception {
        ElevatorManager s = new ElevatorManager(10, 1);

        ElevatorController c = s.getDispatcher().call(1, true).get(); // this waits
        c.selectFloor(5);

        s.setMaintenance(c.getGroupId(), true); // allow current operations to finish

        c.selectFloor(3); // new selections are ignored
        Thread.sleep(11_000L);
        assertEquals(5, c.getCurrentFloor());

        s.setMaintenance(c.getGroupId(), false);

        c.selectFloor(3);

        Thread.sleep(7_000L);
        assertEquals(3, c.getCurrentFloor());
    }
}
