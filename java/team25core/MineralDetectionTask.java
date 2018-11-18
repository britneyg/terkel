package team25core;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.robotcore.external.tfod.TFObjectDetector;

import java.util.ArrayList;
import java.util.List;

import static org.firstinspires.ftc.robotcore.external.tfod.TfodRoverRuckus.TFOD_MODEL_ASSET;

public class MineralDetectionTask extends RobotTask {

    public enum EventKind {
        OBJECTS_DETECTED,
    }

    protected ElapsedTime timer;

    public class MineralDetectionEvent extends RobotEvent {

        public EventKind kind;
        public List<Recognition> minerals;

        public MineralDetectionEvent(RobotTask task, EventKind kind, List<Recognition> minerals)
        {
            super(task);
            this.kind = kind;
            this.minerals.addAll(minerals);
        }

        public String toString()
        {
            return kind.toString();
        }
    }

    private VuforiaLocalizer vuforia;
    private Telemetry telemetry;
    private TFObjectDetector tfod;
    private static final String LABEL_GOLD_MINERAL = "Gold Mineral";
    private static final String LABEL_SILVER_MINERAL = "Silver Mineral";
    private int rateLimitSecs;
    private DetectionKind detectionKind;

    public enum DetectionKind {
        EVERYTHING,
        GOLD,
        SILVER,
        LARGEST_GOLD,
    };

    public enum MineralKind {
        GOLD_MINERAL,
        SILVER_MINERAL,
        UNKNOWN_MINERAL,
    };

    public MineralDetectionTask(Robot robot)
    {
        super(robot);

        rateLimitSecs = 0;
        detectionKind = DetectionKind.EVERYTHING;
    }

    private void initVuforia() {
        /*
         * Configure Vuforia by creating a Parameter object, and passing it to the Vuforia engine.
         */
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters();

        parameters.vuforiaLicenseKey = VuforiaConstants.VUFORIA_KEY;
        parameters.cameraDirection = VuforiaLocalizer.CameraDirection.BACK;

        //  Instantiate the Vuforia engine
        vuforia = ClassFactory.getInstance().createVuforia(parameters);

        // Loading trackables is not necessary for the Tensor Flow Object Detection engine.
    }

    private void initTfod(HardwareMap hardwareMap)
    {
        int tfodMonitorViewId = hardwareMap.appContext.getResources().getIdentifier(
                "tfodMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        TFObjectDetector.Parameters tfodParameters = new TFObjectDetector.Parameters(tfodMonitorViewId);
        tfod = ClassFactory.getInstance().createTFObjectDetector(tfodParameters, vuforia);
        tfod.loadModelFromAsset(TFOD_MODEL_ASSET, LABEL_GOLD_MINERAL, LABEL_SILVER_MINERAL);
    }

    public void init(Telemetry telemetry, HardwareMap hardwareMap)
    {
        initVuforia();

        if (ClassFactory.getInstance().canCreateTFObjectDetector()) {
            initTfod(hardwareMap);
        } else {
            telemetry.addData("Sorry!", "This device is not compatible with TFOD");
        }
    }

    public void rateLimit(int seconds)
    {
        this.rateLimitSecs = seconds;
    }

    public void setDetectionKind(DetectionKind detectionKind)
    {
        this.detectionKind = detectionKind;
    }

    @Override
    public void start()
    {
        tfod.activate();

        if (rateLimitSecs != 0) {
            timer = new ElapsedTime(ElapsedTime.Resolution.SECONDS);
        }
    }

    @Override
    public void stop()
    {
        tfod.deactivate();
    }

    public static MineralKind isMineral(Recognition object)
    {
        if (object.getLabel().equals(LABEL_GOLD_MINERAL)) {
            return MineralKind.GOLD_MINERAL;
        } else if (object.getLabel().equals(LABEL_SILVER_MINERAL)) {
            return MineralKind.SILVER_MINERAL;
        } else {
            return MineralKind.UNKNOWN_MINERAL;
        }
    }

    protected void processEverything(List<Recognition> objects)
    {
        if (objects.size() > 0) {
            robot.queueEvent(new MineralDetectionEvent(this, EventKind.OBJECTS_DETECTED, objects));
        }
    }

    protected void processGold(List<Recognition> objects)
    {
        List<Recognition> gold = new ArrayList<>();
        for (Recognition object : objects) {
            if (isMineral(object) == MineralKind.GOLD_MINERAL) {
                gold.add(object);
            }
        }

        if (!gold.isEmpty()) {
            robot.queueEvent(new MineralDetectionEvent(this, EventKind.OBJECTS_DETECTED, gold));
        }
    }

    protected void processSilver(List<Recognition> objects)
    {
        List<Recognition> silver = new ArrayList<>();
        for (Recognition object : objects) {
            if (isMineral(object) == MineralKind.SILVER_MINERAL) {
                silver.add(object);
            }
        }

        if (!silver.isEmpty()) {
            robot.queueEvent(new MineralDetectionEvent(this, EventKind.OBJECTS_DETECTED, silver));
        }

    }

    protected void processLargestGold(List<Recognition> objects)
    {
        if (objects.isEmpty()) {
            return;
        }

        Recognition largest = null;
        List<Recognition> singleton;

        for (Recognition object : objects) {
            if (isMineral(object) == MineralKind.GOLD_MINERAL) {
                if (largest == null) {
                    largest = object;
                } else if ((largest.getHeight() * largest.getWidth()) < (object.getWidth() * object.getHeight())) {
                    largest = object;
                }
            }
        }

        if (largest != null) {
            singleton = new ArrayList<>();
            singleton.add(largest);
            robot.queueEvent(new MineralDetectionEvent(this, EventKind.OBJECTS_DETECTED, singleton));
        }
    }

    protected void processDetectedObjects(List<Recognition> objects)
    {
        switch (detectionKind) {
            case EVERYTHING:
                processEverything(objects);
                break;
            case GOLD:
                processGold(objects);
                break;
            case SILVER:
                processSilver(objects);
                break;
            case LARGEST_GOLD:
                processLargestGold(objects);
                break;
        }
    }

    @Override
    public boolean timeslice()
    {
        if (rateLimitSecs != 0) {
            if (timer.time() < 3) {
                return false;
            }
        }

        processDetectedObjects(tfod.getUpdatedRecognitions());

        if (rateLimitSecs != 0) {
            timer.reset();
        }

        return false;
    }
}