package co.haveanidea.typer;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.LinearLayout;
import java.util.*;

/** Intercepts a deliberate drag across letter keys; ordinary taps remain native buttons. */
final class GlideLayout extends LinearLayout {
    interface Listener { void onTrace(String trace); }
    private final Map<View,String> letters=new LinkedHashMap<>();
    private final List<PointF> points=new ArrayList<>();
    private final StringBuilder trace=new StringBuilder();
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float downX,downY;
    private boolean started,gliding;
    boolean enabled;
    Listener listener;
    GlideLayout(Context context) { super(context);setOrientation(VERTICAL);setWillNotDraw(false);ink.setColor(0x997BAC93);ink.setStrokeWidth(9);ink.setStrokeCap(Paint.Cap.ROUND); }
    void register(View view,String letter) { letters.put(view,letter); }
    private String letterAt(float x,float y) {
        for(Map.Entry<View,String> e:letters.entrySet()) {
            Rect r=new Rect();e.getKey().getDrawingRect(r);offsetDescendantRectToMyCoords(e.getKey(),r);
            if(r.contains((int)x,(int)y)) return e.getValue();
        }
        return "";
    }
    private void record(MotionEvent event) {
        if(points.size()>400) points.remove(0);
        points.add(new PointF(event.getX(),event.getY()));
        String c=letterAt(event.getX(),event.getY());
        if(!c.isEmpty()&&(trace.length()==0||!trace.substring(trace.length()-1).equals(c))) trace.append(c);
        if(trace.length()>120) { trace.delete(0,trace.length()-120); }
        invalidate();
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if(!enabled)return false;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
            downX=e.getX();downY=e.getY();started=!letterAt(downX,downY).isEmpty();gliding=false;trace.setLength(0);points.clear();if(started)record(e);
        } else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&started) {
            if(Math.hypot(e.getX()-downX,e.getY()-downY)>24*getResources().getDisplayMetrics().density) { gliding=true;record(e);return true; }
        } else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL) {points.clear();invalidate();}
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(!gliding)return super.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE) record(e);
        if(e.getActionMasked()==MotionEvent.ACTION_UP) {record(e);if(listener!=null)listener.onTrace(trace.toString());gliding=false;points.clear();invalidate();performClick();}
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){gliding=false;points.clear();invalidate();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if(gliding)for(int i=1;i<points.size();i++)canvas.drawLine(points.get(i-1).x,points.get(i-1).y,points.get(i).x,points.get(i).y,ink);
    }
}
