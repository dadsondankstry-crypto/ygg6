package com.guardiao.dados;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gráfico compacto do tráfego agregado do aparelho (não por aplicativo).
 * Amostras: soma de download+upload em bytes por segundo a cada 2s.
 * Não realiza rede nem mantém atividade em segundo plano.
 */
public class TrafficChartView extends View {
    private final Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Long> samples = new ArrayList<>();

    public TrafficChartView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        grid.setColor(0xFFE4EBF2);
        grid.setStrokeWidth(dp(1));
        label.setColor(0xFF516780);
        label.setTextSize(dp(11));
        bars.setColor(0xFF1376BA);
    }

    public void setSamples(List<Long> values) {
        samples.clear();
        if (values != null) samples.addAll(values);
        invalidate();
    }

    private float dp(float x) { return getResources().getDisplayMetrics().density * x; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float left = dp(14), right = w - dp(10), top = dp(25), bottom = h - dp(23);
        if (right <= left || bottom <= top) return;
        canvas.drawLine(left, bottom, right, bottom, grid);
        canvas.drawLine(left, (top+bottom)/2, right, (top+bottom)/2, grid);
        long max = 1;
        for (long n : samples) if (n > max) max = n;
        label.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Últimas " + Math.min(120, samples.size()*2) + " s", left, dp(16), label);
        label.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatRate(max), right, dp(16), label);
        if (samples.isEmpty()) {
            label.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Aguardando amostras...", w/2, (top+bottom)/2, label);
            return;
        }
        float gap = dp(1.5f);
        float width = (right-left) / 60f;
        int offset = 60 - samples.size();
        for (int i=0; i<samples.size(); i++) {
            float x = left + (offset+i)*width;
            float height = Math.max(dp(1), (samples.get(i)/(float)max)*(bottom-top));
            canvas.drawRoundRect(x, bottom-height, x+Math.max(dp(1),width-gap), bottom,
                dp(1),dp(1),bars);
        }
        label.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Tráfego global • 2s/amostra", left, h-dp(6), label);
    }

    private String formatRate(long n) {
        if (n >= 1024*1024) return String.format(Locale.getDefault(),"%.1f MB/s", n/1048576.0);
        if (n >= 1024) return String.format(Locale.getDefault(),"%.1f KB/s", n/1024.0);
        return n+" B/s";
    }
}
