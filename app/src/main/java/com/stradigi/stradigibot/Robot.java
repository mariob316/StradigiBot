package com.stradigi.stradigibot;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.hardware.SensorEvent;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.util.Log;
import com.zugaldia.adafruit.motorhat.library.AdafruitDCMotor;
import com.zugaldia.adafruit.motorhat.library.AdafruitMotorHat;
import java.lang.annotation.Retention;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Created by Mario on 2017-04-12.
 */

public class Robot implements RobotInterface, LifecycleObserver {

  private final static String TAG = "ROBOT";

  public static final int DISTANCE_VALUE = 0;
  public static final int MAX_DISTANCE_FROM_OBJ = 20;
  public static final int SAFE_DISTANCE_TO_OBJ = 60;
  public static final int DEFAULT_SPEED = 200;

  @Retention(SOURCE) @IntDef({ FORWARD, BACKWARD, LEFT, RIGHT, STOP }) private @interface ICommand {
  }

  private static final int STOP = 0;
  private static final int BACKWARD = 1;
  private static final int FORWARD = 2;
  private static final int LEFT = 3;
  private static final int RIGHT = 4;

  private AdafruitMotorHat mh;
  private RobotEyes robotEyes;

  @ICommand private volatile int command = 0;
  private volatile int speed;
  private volatile boolean run = false;
  private static final int PERIOD = 255;

  public Robot(LifecycleOwner owner) {
    this.mh = new AdafruitMotorHat();
    this.robotEyes = new RobotEyes(this);
    setMotorSpeed(DEFAULT_SPEED);
    owner.getLifecycle().addObserver(this);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START) void start() {
    robotThread.start();
  }

  private int validateSpeed(int speed) {
    return Math.max(0, Math.min(255, speed));
  }

  public void forward() {
    forward(DEFAULT_SPEED);
  }

  public void backward() {
    backward(DEFAULT_SPEED);
  }

  public void left() {
    left(DEFAULT_SPEED);
  }

  public void right() {
    right(DEFAULT_SPEED);
  }

  public void sleep(int time) {

  }

  @Override public synchronized void forward(@IntRange(from = 0, to = 255) int speed) {
    setMotorSpeed(speed);
    if (isMovingForward()) return;
    if (!robotThread.isAlive()) {
      goForward();
      return;
    }

    command = FORWARD;
    run = true;
  }

  @Override public synchronized void backward(@IntRange(from = 0, to = 255) int speed) {
    setMotorSpeed(speed);
    if (!robotThread.isAlive()) {
      goBackwards();
      return;
    }

    command = BACKWARD;
    run = true;
  }

  @Override public synchronized void left(@IntRange(from = 0, to = 255) int speed) {
    Log.d("LEFT", "Turn Left");
    if (isTurningLeft()) return;
    setMotorSpeed(speed);
    if (!robotThread.isAlive()) {
      goLeft();
      return;
    }

    Log.d("LEFT", "About to turn Left");
    command = LEFT;
    run = true;
  }

  @Override public synchronized void right(@IntRange(from = 0, to = 255) int speed) {
    setMotorSpeed(speed);
    if (!robotThread.isAlive()) {
      goRight();
      return;
    }

    command = RIGHT;
    run = true;
  }

  @Override public synchronized void stop() {
    setMotorSpeed(0);
    stopAll();
    command = STOP;
    run = false;
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP) void shutDown() {
    Log.i(TAG, "!!!!Shutdown!!");
    robotEyes.close();
    run = false;
    if (robotThread.isAlive()) {
      robotThread.interrupt();
    }
    stop();
    mh.close();
  }

  private AdafruitDCMotor getMotor(@IntRange(from = 1, to = 4) int position) {
    return mh.getMotor(position);
  }

  private void stopAll() {
    for (AdafruitDCMotor motor : mh.getMotors()) {
      motor.run(AdafruitMotorHat.RELEASE);
    }
  }

  private void goForward() {
    for (AdafruitDCMotor motor : mh.getMotors()) {
      motor.run(AdafruitMotorHat.FORWARD);
    }
  }

  private void goBackwards() {
    for (AdafruitDCMotor motor : mh.getMotors()) {
      motor.run(AdafruitMotorHat.BACKWARD);
    }
  }

  private void goLeft() {
    int count = 0;
    for (AdafruitDCMotor motor : mh.getMotors()) {
      if (count == 0 || count == 3) {
        motor.run(AdafruitMotorHat.RELEASE);
      } else {
        motor.run(AdafruitMotorHat.FORWARD);
      }
      ++count;
    }
  }

  private void goRight() {
    int count = 0;
    for (AdafruitDCMotor motor : mh.getMotors()) {
      if (count == 1 || count == 2) {
        motor.run(AdafruitMotorHat.RELEASE);
      } else {
        motor.run(AdafruitMotorHat.FORWARD);
      }
      ++count;
    }
  }

  private void setMotorSpeed(@IntRange(from = 0, to = 255) int speed) {
    this.speed = speed;
    for (AdafruitDCMotor motor : mh.getMotors()) {
      motor.setSpeed(validateSpeed(speed));
    }
  }

  @Override public synchronized void reduceSpeed() {
    if (speed <= 10) return;
    this.speed -= 10;
    //Log.d("Reduc", "Reducing speed to: " + String.valueOf(speed));
    //if (this.speed < 20) {
    //  Log.d("Reduc", "Stopping due to speed: " + String.valueOf(speed));
    //  //stop();
    //  return;
    //}
    setMotorSpeed(speed);
  }

  /// Set meter per second speed ///
  //// with pWValue = 200 it goes .2 mps => conversionFactor = 200/0.2
  float conversionFactor = 1.0f;
  float chassisDiameter = .35f; /// in meter

  private void setMotorSpeedMPS(float mps) {
    this.speed = (int) (conversionFactor * mps);
    setMotorSpeed(speed);
  }

  private float getMps() {
    return this.speed / conversionFactor;
  }

  @Override public void turnLeftByDegrees(int degrees) {
    if (isTurningLeft() || isStopped()) return;
    setMotorSpeed(DEFAULT_SPEED);
    goLeft();

    double total = 2 * Math.PI * chassisDiameter;
    float distanceShouldGo = (float) (total * (Math.abs(degrees) / 360f));

    /// lets say current speed is mps ///
    float mps = getMps();
    final long timeToLeftMillis = (long) ((1000) * (distanceShouldGo / mps));

    Log.d("LEFT", "TUrning left: " + String.valueOf(timeToLeftMillis));
    /// turn left and Stop after
    new Handler().postDelayed(new Runnable() {
      @Override public void run() {
        stop();
      }
    }, 2000);
  }

  public void turnRight(int degrees) {
    goRight();

    double total = 2 * Math.PI * chassisDiameter;
    float distanceShouldGo = (float) (total * (degrees / 360f));

    /// lets say current speed is mps ///
    float mps = getMps();
    final long timeToRightMillis = (long) ((1000) * (distanceShouldGo / mps));

    /// turn right and Stop after
    new Handler().postDelayed(new Runnable() {
      @Override public void run() {
        command = STOP;
      }
    }, timeToRightMillis);
  }

  private Thread robotThread = new Thread(new Runnable() {

    @Override public void run() {
      int curSpeed;
      @ICommand int curCmd;
      while (run) {
        curSpeed = speed;
        curCmd = command;
        try {
          if (curSpeed == 0 || curCmd == STOP) {
            stopAll();
            TimeUnit.MICROSECONDS.sleep(PERIOD);
          } else {
            switch (curCmd) {
              case FORWARD:
                goForward();
                break;
              case BACKWARD:
                goBackwards();
                break;
              case LEFT:
                Log.d("CMD", "Command LEFT");
                goLeft();
                break;
              case RIGHT:
                goRight();
                break;
              default:
                break;
            }
            TimeUnit.MICROSECONDS.sleep(PERIOD);
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  });

  public boolean isMovingForward() {
    return getMotor(1).isGoingForwards() && getMotor(2).isGoingForwards();
  }

  public boolean isMovingBackwards() {
    return getMotor(1).isGoingBackwards() && getMotor(2).isGoingBackwards();
  }

  public boolean isTurningLeft() {
    //if (count == 0 || count == 3) {
    //  motor.run(AdafruitMotorHat.RELEASE);
    //} else {
    //  motor.run(AdafruitMotorHat.FORWARD);
    //}

    boolean m1 = getMotor(1).isRunning();
    boolean m2 = getMotor(2).isRunning();
    boolean m3 = getMotor(3).isRunning();
    boolean m4 = getMotor(4).isRunning();

    Log.d(TAG, " " + m1 + " " + m2 + " " + m3 + " " + m4);

    return !m1 && !m4 && m2 && m3;
  }

  public boolean isTurningRight() {
    return getMotor(1).isRunning()
        && getMotor(4).isRunning()
        && !getMotor(2).isRunning()
        && !getMotor(3).isRunning();
  }

  public boolean isStopped() {
    for (AdafruitDCMotor motor : mh.getMotors()) {
      if (motor.isRunning()) {
        return false;
      }
    }
    return true;
  }

  public void handleProximitySensorData(SensorEvent event) {
    robotEyes.watch(event);
  }
}
