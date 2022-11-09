package com.luxand.livenessrecognition;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;

import com.luxand.FSDK;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

class FaceLocator {

    private enum Command {
        TURN_LEFT,
        TURN_RIGHT,
        LOOK_STRAIGHT,
      /*  LOOK_UP,
        LOOK_DOWN,*/
        SMILE
    }

    private static Set<Command> LEFT_RIGHT = new HashSet<Command>() {{ add(Command.TURN_LEFT); add(Command.TURN_RIGHT); }};
    private static Set<Command> UP_DOWN = new HashSet<Command>() {{  }};

    private static int[] LEFT_EYE = new int[] {
            FSDK.FSDKP_LEFT_EYE,
            FSDK.FSDKP_LEFT_EYE_INNER_CORNER,
            FSDK.FSDKP_LEFT_EYE_OUTER_CORNER,
            FSDK.FSDKP_LEFT_EYE_LOWER_LINE1,
            FSDK.FSDKP_LEFT_EYE_LOWER_LINE2,
            FSDK.FSDKP_LEFT_EYE_LOWER_LINE3,
            FSDK.FSDKP_LEFT_EYE_UPPER_LINE1,
            FSDK.FSDKP_LEFT_EYE_UPPER_LINE2,
            FSDK.FSDKP_LEFT_EYE_UPPER_LINE3,
            FSDK.FSDKP_LEFT_EYE_RIGHT_IRIS_CORNER,
            FSDK.FSDKP_LEFT_EYE_LEFT_IRIS_CORNER
    };

    private static int[] RIGHT_EYE = new int[] {
            FSDK.FSDKP_RIGHT_EYE,
            FSDK.FSDKP_RIGHT_EYE_INNER_CORNER,
            FSDK.FSDKP_RIGHT_EYE_OUTER_CORNER,
            FSDK.FSDKP_RIGHT_EYE_LOWER_LINE1,
            FSDK.FSDKP_RIGHT_EYE_LOWER_LINE2,
            FSDK.FSDKP_RIGHT_EYE_LOWER_LINE3,
            FSDK.FSDKP_RIGHT_EYE_UPPER_LINE1,
            FSDK.FSDKP_RIGHT_EYE_UPPER_LINE2,
            FSDK.FSDKP_RIGHT_EYE_UPPER_LINE3,
            FSDK.FSDKP_RIGHT_EYE_RIGHT_IRIS_CORNER,
            FSDK.FSDKP_RIGHT_EYE_LEFT_IRIS_CORNER
    };

    private static Random random = new Random();

    private static Paint facePaint = new Paint();
    private static Paint activePaint = new Paint();
    private static Paint featuresPaint = new Paint();
    private static TextPaint textPaint = new TextPaint();
    private static TextPaint greenPaint = new TextPaint();
    private static TextPaint shadowPaint = new TextPaint();

    static {
    	facePaint.setColor(Color.WHITE);
    	facePaint.setStyle(Paint.Style.STROKE);
    	facePaint.setStrokeWidth(7);

    	activePaint.setColor(Color.RED);
    	activePaint.setStyle(Paint.Style.STROKE);
    	activePaint.setStrokeWidth(4);

    	featuresPaint.setColor(Color.YELLOW);
    	featuresPaint.setStyle(Paint.Style.FILL);

    	textPaint.setColor(Color.WHITE);
    	textPaint.setStyle(Paint.Style.FILL);
    	textPaint.setTextSize(18 * MainActivity.sDensity);

    	greenPaint.setColor(Color.GREEN);
    	greenPaint.setStyle(Paint.Style.FILL);
    	greenPaint.setTextSize(18 * MainActivity.sDensity);

        shadowPaint.setColor(Color.BLACK);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setTextSize(18 * MainActivity.sDensity);
	}

    private long faceId;
    private int commandId;
    private int countdown = 35;

    private float ratio;
    private double angle;

    private FaceRectangle frame = new FaceRectangle();

    private boolean live = false;

    private Point center = new Point();

    private LowPassFilter lpf = null;
    private LowPassFilter pan = new LowPassFilter();
    private LowPassFilter tilt = new LowPassFilter();

    private FSDK.HTracker tracker;

    private List<Command> commands;
    private Set<Command> state = new HashSet<Command>() {{ add(Command.LOOK_STRAIGHT); }};

    private boolean active   = false;

    private static Map<Command, String> commandNames = new HashMap<Command, String>() {{
        put(Command.TURN_LEFT, "Turn Left");
        put(Command.TURN_RIGHT, "Turn Right");
        put(Command.LOOK_STRAIGHT, "Look straight");
        /*put(Command.LOOK_UP, "Look up");
        put(Command.LOOK_DOWN, "Look down");*/
        put(Command.SMILE, "Make a smile");
    }};

    public FaceLocator(long faceId, final FSDK.HTracker tracker, final float ratio) {
        this.faceId = faceId;
        this.tracker = tracker;
        this.ratio = ratio;
    }

    public boolean doesIntersect(final FaceLocator other) {
        return !(
                frame.x1 >= other.frame.x2 ||
                        frame.x2 < other.frame.x1 ||
                        frame.y1 >= other.frame.y2 ||
                        frame.y2 < other.frame.y1
        );
    }

    public boolean isInside(double x, double y) {
        x -= center.x * ratio;
        y -= center.y * ratio;

        final double a = angle * Math.PI / 180;
        final double xx = x * Math.cos(a) + y * Math.sin(a);
        final double yy = x * Math.sin(a) - y * Math.cos(a);

        return Math.pow(xx / frame.x1 / ratio, 2) + Math.pow(yy / frame.y1 / ratio, 2) <= 1;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active)
            return;

        live = false;
        commandId = 0;

        commands = new ArrayList<Command>() {{ add(Command.LOOK_STRAIGHT); }};

        while (commands.size() < 4) {
            Command cmd = Command.values()[random.nextInt(4)];
            if (commands.get(commands.size() - 1) != cmd)
                commands.add(cmd);
        }

        if (!commands.contains(Command.SMILE))
            commands.set(random.nextInt(commands.size() - 1), Command.SMILE);
    }

    private boolean approveCommand(final Command command) {
        if (!state.contains(command))
            return false;

        if (command == Command.SMILE)
            return true;

        return !state.contains(Command.SMILE);
    }

    public void updateState() {
        final String[] result = new String[] { "" };
        if (FSDK.GetTrackerFacialAttribute(tracker, 0, faceId, "Expression", result, 256) != FSDK.FSDKE_OK)
            return;

        double smile = 0;

        for (String pair : result[0].split(";")) {
            String[] values = pair.split("=");
            if (values[0].equalsIgnoreCase("smile")) {
                smile = Double.parseDouble(values[1]);
                break;
            }
        }

        double pan = 0;
        double tilt = 0;

        if (FSDK.GetTrackerFacialAttribute(tracker, 0, faceId, "Angles", result, 256) != FSDK.FSDKE_OK)
            return;

        for (String pair : result[0].split(";")) {
            String[] values = pair.split("=");

            if (values[0].equalsIgnoreCase("pan")) {
                pan = this.pan.pass(Float.parseFloat(values[1]));
                continue;
            }

            if (values[0].equalsIgnoreCase("tilt")) {
                tilt = this.tilt.pass(Float.parseFloat(values[1]));
            }
        }

        Command st = null;

        final double HOR_CONF_LEVEL_LOW = 2;
        final double HOR_CONF_LEVEL_HIGH = 17;

        if (Math.abs(pan) < HOR_CONF_LEVEL_LOW)
            st = Command.LOOK_STRAIGHT;
        else if (Math.abs(pan) > HOR_CONF_LEVEL_HIGH)
            st = pan > 0 ? Command.TURN_LEFT : Command.TURN_RIGHT; // swapped for mirrored feed from front camera

        if (st != null) {
            state.removeAll(LEFT_RIGHT);
            state.add(st);
        }

        final double UP_CONF_LEVEL_LOW = 2;
        final double UP_CONF_LEVEL_HIGH = 5;
        final double DOWN_CONF_LEVEL_LOW = -2;
        final double DOWN_CONF_LEVEL_HIGH = -5;

        st = null;

       /* if (tilt > UP_CONF_LEVEL_HIGH)
            st = Command.LOOK_UP;
        else if (tilt < DOWN_CONF_LEVEL_HIGH)
            st = Command.LOOK_DOWN;
        else*/ if (tilt > DOWN_CONF_LEVEL_LOW && tilt < UP_CONF_LEVEL_LOW)
            st = Command.LOOK_STRAIGHT;

        if (st != null) {
            state.removeAll(UP_DOWN);
            state.add(st);
        }

        if (/*state.contains(Command.LOOK_UP)   || state.contains(Command.LOOK_DOWN) ||*/
                state.contains(Command.TURN_LEFT) || state.contains(Command.TURN_RIGHT))
            state.remove(Command.LOOK_STRAIGHT);

        final double SMILE_CONF_LEVEL_LOW = 0.3;
        final double SMILE_CONF_LEVEL_HIGH = 0.6;

        if (smile < SMILE_CONF_LEVEL_LOW)
            state.remove(Command.SMILE);
        else if (smile > SMILE_CONF_LEVEL_HIGH)
            state.add(Command.SMILE);

        if (active && !live)
            if (approveCommand(commands.get(commandId))) {
                ++commandId;
                if (commandId >= commands.size())
                    live = true;
            }
    }

    private static FSDK.TPoint dotCenter(FSDK.TPoint[] points) {
        FSDK.TPoint result = new FSDK.TPoint() {{
            x = 0;
            y = 0;
        }};


        for (FSDK.TPoint point : points) {
            result.x += point.x;
            result.y += point.y;
        }

        result.x /= points.length;
        result.y /= points.length;

        return result;
    }

    private static FSDK.TPoint[] getPoints(FSDK.FSDK_Features features, int[] indices) {
        FSDK.TPoint[] result = new FSDK.TPoint[indices.length];

        for (int i = 0; i < result.length; ++i)
            result[i] = features.features[indices[i]];

        return result;
    }

    private void drawShape(Canvas canvas) {
    	float left   = (center.x + frame.x1) * ratio;
		float top    = (center.y + frame.y1) * ratio;
		float right  = (center.x + frame.x2) * ratio;
		float bottom = (center.y + frame.y2) * ratio;

        canvas.drawOval(left, top, right, bottom, facePaint);

        if (active)
            canvas.drawOval(left, top, right, bottom, activePaint);
    }

    public boolean draw(Canvas canvas, ButtonEvents buttonEvents) {
        FSDK.FSDK_Features features = new FSDK.FSDK_Features();

        if (FSDK.GetTrackerFacialFeatures(tracker, 0, faceId, features) != FSDK.FSDKE_OK) {
            countdown -= 1;

            if (countdown <= 8) {
                frame.x1 *= 0.95;
                frame.y1 *= 0.95;
                frame.x2 *= 0.95;
                frame.y2 *= 0.95;
            }

            drawShape(canvas);
            return countdown > 0;
        }

        if (lpf == null)
            lpf = new LowPassFilter();

        FSDK.TPoint left = dotCenter(getPoints(features, LEFT_EYE));
        FSDK.TPoint right = dotCenter(getPoints(features, RIGHT_EYE));

        float w = lpf.pass((right.x - left.x) * 2.8f);
        float h = w * 1.4f;

        center.x = (right.x + left.x) / 2.f;
        center.y = (right.y + left.y) / 2.f + w * 0.05f;
        angle = Math.atan2(right.y - left.y, right.x - left.x) * 180 / Math.PI;

        frame.set(w, h);

        drawShape(canvas);

        if (!active)
            return true;

// Draw facial features
//        for (FSDK.TPoint point : features.features)
//            canvas.drawCircle(point.x * ratio, point.y * ratio, 2, featuresPaint);

        String name = live ? "LIVE!" : "";
        TextPaint style = live ? greenPaint : textPaint;

        if (isActive())
            if (commandId >= commands.size()) {
                buttonEvents.enableSubmit();
                name = "LIVE!";
            } else
                name = String.format("%s (%s of %s)",
                        commandNames.get(commands.get(commandId)), commandId + 1, commands.size());

        if (name.length() > 0) {
            canvas.drawText(name, center.x - w / 2 + 5, center.y - h / 2 + 5, shadowPaint);
            canvas.drawText(name, center.x - w / 2, center.y - h / 2, style);
        }

        updateState();
        countdown = 35;

        return true;
    }
}