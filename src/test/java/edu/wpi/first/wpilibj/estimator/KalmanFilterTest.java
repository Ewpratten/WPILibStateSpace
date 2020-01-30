package edu.wpi.first.wpilibj.estimator;

import edu.wpi.first.wpilibj.controller.LinearQuadraticRegulatorTest;
import edu.wpi.first.wpilibj.geometry.Pose2d;
import edu.wpi.first.wpilibj.geometry.Rotation2d;
import edu.wpi.first.wpilibj.geometry.Transform2d;
import edu.wpi.first.wpilibj.geometry.Translation2d;
import edu.wpi.first.wpilibj.math.StateSpaceUtils;
import edu.wpi.first.wpilibj.system.LinearSystem;
import edu.wpi.first.wpilibj.trajectory.TrajectoryConfig;
import edu.wpi.first.wpilibj.trajectory.TrajectoryGenerator;
import edu.wpi.first.wpiutil.math.MatBuilder;
import edu.wpi.first.wpiutil.math.Matrix;
import edu.wpi.first.wpiutil.math.Nat;
import edu.wpi.first.wpiutil.math.numbers.N3;
import edu.wpi.first.wpiutil.math.numbers.N6;
import javafx.scene.chart.XYChart;
import org.ejml.simple.SimpleMatrix;
import org.junit.Assert;
import org.junit.Test;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChartBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static edu.wpi.first.wpilibj.controller.LinearSystemLoopTest.kDt;

public class KalmanFilterTest {

    static {
        LinearQuadraticRegulatorTest.createArm();
        LinearQuadraticRegulatorTest.createElevator();
    }

    @Test
    public void testElevatorKalmanFilter() {
        var plant = LinearQuadraticRegulatorTest.elevatorPlant;

        var Q = new MatBuilder<>(Nat.N2(), Nat.N1()).fill(0.05, 1.0);
        var R = new MatBuilder<>(Nat.N1(), Nat.N1()).fill(0.0001);

        var filter = new KalmanFilter<>(Nat.N2(), Nat.N1(), Nat.N1(), plant, Q, R, kDt);

        var p = filter.getP();
        var gain = filter.getXhat();

        System.out.printf("p: \n%s\n: gain: \n%s\n", p, gain);
    }

    // A swerve drive system where the states are [x, y, theta, vx, vy, vTheta]^T,
    // Y is [x, y, theta]^T and u is [ax, ay, alpha}^T
    LinearSystem<N6, N3, N3> swerveObserverSystem = new LinearSystem<>(Nat.N6(), Nat.N3(), Nat.N3(),
        new MatBuilder<>(Nat.N6(), Nat.N6()).fill( // A
            0, 0, 0, 1, 0, 0,
            0, 0, 0, 0, 1, 0,
            0, 0, 0, 0, 0, 1,
            0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0),
        new MatBuilder<>(Nat.N6(), Nat.N3()).fill( // B
            0, 0, 0,
            0, 0, 0,
            0, 0, 0,
            1, 0, 0,
            0, 1, 0,
            0, 0, 1
        ),
        new MatBuilder<>(Nat.N3(), Nat.N6()).fill( // C
            1, 0, 0, 0, 0, 0,
            0, 1, 0, 0, 0, 0,
            0, 0, 1, 0, 0, 0
        ),
        new Matrix<>(new SimpleMatrix(3, 3)), // D
        new MatBuilder<>(Nat.N3(), Nat.N1()).fill(-4, -4, -12), // uMin,
        new MatBuilder<>(Nat.N3(), Nat.N1()).fill(4, 4, 12));

    @Test
    public void testSwerveKFStationary() {

        var random = new Random();
        swerveObserverSystem.reset();

        var filter = new KalmanFilter<>(Nat.N6(), Nat.N3(), Nat.N3(),
            swerveObserverSystem,
            new MatBuilder<>(Nat.N6(), Nat.N1()).fill( 0.1, 0.1, 0.1, 0.1, 0.1, 0.1 ), // state weights
            new MatBuilder<>(Nat.N3(), Nat.N1()).fill( 2, 2, 2), // measurement weights
            0.020
        );

        List<Double> xhatsX = new ArrayList<>();
        List<Double> measurementsX = new ArrayList<>();
        List<Double> xhatsY = new ArrayList<>();
        List<Double> measurementsY = new ArrayList<>();

        for(int i = 0; i < 100; i++) {
            // the robot is at [0, 0, 0] so we just park here
            var measurement = new MatBuilder<>(Nat.N3(), Nat.N1()).fill(
                random.nextGaussian(), random.nextGaussian(), random.nextGaussian() // std dev of [1, 1, 1]
            );
            filter.correct(new MatBuilder<>(Nat.N3(), Nat.N1()).fill(0.0, 0.0, 0.0), measurement);

            // we continue to not accelerate
            filter.predict(new MatBuilder<>(Nat.N3(), Nat.N1()).fill(0.0, 0.0, 0.0), 0.020);

            measurementsX.add(measurement.get(0, 0));
            measurementsY.add(measurement.get(1, 0));
            xhatsX.add(filter.getXhat(0));
            xhatsY.add(filter.getXhat(1));
        }

//        var chart = new XYChartBuilder().build();
//        chart.addSeries("Xhat, x/y", xhatsX, xhatsY);
//        chart.addSeries("Measured position, x/y", measurementsX, measurementsY);
//        try {
//            new SwingWrapper<>(chart).displayChart();
//            Thread.sleep(10000000);
//        } catch (Exception ign) {}
        Assert.assertEquals(0.0, swerveObserverSystem.getX(0), 0.1);
        Assert.assertEquals(0.0, swerveObserverSystem.getX(0), 0.1);
    }

    @Test
    public void testSwerveKFMovingWithoutAccelerating() {

        var random = new Random();
        swerveObserverSystem.reset();

        var filter = new KalmanFilter<>(Nat.N6(), Nat.N3(), Nat.N3(),
            swerveObserverSystem,
            new MatBuilder<>(Nat.N6(), Nat.N1()).fill( 0.1, 0.1, 0.1, 0.1, 0.1, 0.1 ), // state weights
            new MatBuilder<>(Nat.N3(), Nat.N1()).fill( 4, 4, 4), // measurement weights
            0.020
        );

        List<Double> xhatsX = new ArrayList<>();
        List<Double> measurementsX = new ArrayList<>();
        List<Double> xhatsY = new ArrayList<>();
        List<Double> measurementsY = new ArrayList<>();

        // we set the velocity of the robot so that it's moving forward slowly
        swerveObserverSystem.setX(0, .5);
        swerveObserverSystem.setX(1, .5);

        for(int i = 0; i < 300; i++) {
            // the robot is at [0, 0, 0] so we just park here
            var measurement = new MatBuilder<>(Nat.N3(), Nat.N1()).fill(
                random.nextGaussian() / 10d,
                random.nextGaussian() / 10d,
                random.nextGaussian() / 4d // std dev of [1, 1, 1]
            );

            filter.correct(new MatBuilder<>(Nat.N3(), Nat.N1()).fill(0.0, 0.0, 0.0), measurement);

            // we continue to not accelerate
            filter.predict(new MatBuilder<>(Nat.N3(), Nat.N1()).fill(0.0, 0.0, 0.0), 0.020);

            measurementsX.add(measurement.get(0, 0));
            measurementsY.add(measurement.get(1, 0));
            xhatsX.add(filter.getXhat(0));
            xhatsY.add(filter.getXhat(1));
        }

//        var chart = new XYChartBuilder().build();
//        chart.addSeries("Xhat, x/y", xhatsX, xhatsY);
//        chart.addSeries("Measured position, x/y", measurementsX, measurementsY);
//        try {
//            new SwingWrapper<>(chart).displayChart();
//            Thread.sleep(10000000);
//        } catch (Exception ign) {}

        Assert.assertEquals(0.0, swerveObserverSystem.getX(0), 0.2);
        Assert.assertEquals(0.0, swerveObserverSystem.getX(1), 0.2);
    }


}
