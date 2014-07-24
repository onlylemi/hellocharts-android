package lecho.lib.hellocharts.renderer;

import lecho.lib.hellocharts.ChartCalculator;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.LinePoint;
import lecho.lib.hellocharts.model.SelectedValue;
import lecho.lib.hellocharts.view.LineChartView;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

public class LineChartRenderer implements ChartRenderer {
	private static final float LINE_SMOOTHNES = 0.16f;
	private static final int MODE_DRAW = 0;
	private static final int MODE_HIGHLIGHT = 1;
	private int labelOffset;
	private int mLabelMargin;
	private int pointRadius;
	private int touchTolleranceMargin;
	private Path mLinePath = new Path();
	private Paint mLinePaint = new Paint();
	private Paint mPointPaint = new Paint();
	private Paint labelPaint = new Paint();
	private RectF labelRect = new RectF();
	private LineChartView mChart;
	private SelectedValue mSelectedValue = new SelectedValue();
	private char[] labelBuffer = new char[32];
	private FontMetricsInt fontMetrics = new FontMetricsInt();

	public LineChartRenderer(LineChartView chart) {
		mChart = chart;
		mLabelMargin = chart.getDefaultLabelMargin();
		labelOffset = chart.getDefaultLabelMargin();
		pointRadius = chart.getDefaultPointRadius();
		touchTolleranceMargin = chart.getDefaultTouchTolleranceMargin();

		mLinePaint.setAntiAlias(true);
		mLinePaint.setStyle(Paint.Style.STROKE);
		mLinePaint.setStrokeWidth(chart.getDefaultLineStrokeWidth());

		mPointPaint.setAntiAlias(true);
		mPointPaint.setStyle(Paint.Style.FILL);

		labelPaint.setAntiAlias(true);
		labelPaint.setStyle(Paint.Style.FILL);
		labelPaint.setTextAlign(Align.LEFT);
		labelPaint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
		labelPaint.setColor(Color.WHITE);
		labelPaint.setTextSize(chart.getDefaultTextSize());
		labelPaint.getFontMetricsInt(fontMetrics);
	}

	@Override
	public void draw(Canvas canvas) {
		final LineChartData data = mChart.getData();
		for (Line line : data.lines) {
			if (line.hasLines()) {
				if (line.isSmooth()) {
					drawSmoothPath(canvas, line);
				} else {
					drawPath(canvas, line);
				}
			}
			mLinePath.reset();
		}
	}

	@Override
	public void drawUnclipped(Canvas canvas) {
		final LineChartData data = mChart.getData();
		int lineIndex = 0;
		for (Line line : data.lines) {
			if (line.hasPoints()) {
				drawPoints(canvas, line, lineIndex, MODE_DRAW);
			}
			mLinePath.reset();
			++lineIndex;
		}
		if (isTouched()) {
			// Redraw touched point to bring it to the front
			highlightPoints(canvas);
		}
	}

	@Override
	public boolean checkTouch(float touchX, float touchY) {
		final LineChartData data = mChart.getData();
		final ChartCalculator chartCalculator = mChart.getChartCalculator();
		int lineIndex = 0;
		for (Line line : data.lines) {
			int valueIndex = 0;
			for (LinePoint linePoint : line.getPoints()) {
				final float rawValueX = chartCalculator.calculateRawX(linePoint.getX());
				final float rawValueY = chartCalculator.calculateRawY(linePoint.getY());
				if (isInArea(rawValueX, rawValueY, touchX, touchY, pointRadius + touchTolleranceMargin)) {
					mSelectedValue.firstIndex = lineIndex;
					mSelectedValue.secondIndex = valueIndex;
				}
				++valueIndex;
			}
			++lineIndex;
		}
		return isTouched();
	}

	@Override
	public boolean isTouched() {
		return mSelectedValue.isSet();
	}

	@Override
	public void clearTouch() {
		mSelectedValue.clear();

	}

	@Override
	public void callTouchListener() {
		mChart.callTouchListener(mSelectedValue);
	}

	public SelectedValue getSelectedValue() {
		return mSelectedValue;
	}

	private void drawPath(Canvas canvas, final Line line) {
		final ChartCalculator chartCalculator = mChart.getChartCalculator();
		int valueIndex = 0;
		for (LinePoint linePoint : line.getPoints()) {
			final float rawValueX = chartCalculator.calculateRawX(linePoint.getX());
			final float rawValueY = chartCalculator.calculateRawY(linePoint.getY());
			if (valueIndex == 0) {
				mLinePath.moveTo(rawValueX, rawValueY);
			} else {
				mLinePath.lineTo(rawValueX, rawValueY);
			}
			++valueIndex;
		}
		mLinePaint.setColor(line.getColor());
		canvas.drawPath(mLinePath, mLinePaint);
		if (line.isFilled()) {
			drawArea(canvas, line.getAreaTransparency());
		}
	}

	private void drawSmoothPath(Canvas canvas, final Line line) {
		final ChartCalculator chartCalculator = mChart.getChartCalculator();
		final int lineSize = line.getPoints().size();
		float previousPointX = Float.NaN;
		float previousPointY = Float.NaN;
		float currentPointX = Float.NaN;
		float currentPointY = Float.NaN;
		float nextPointX = Float.NaN;
		float nextPointY = Float.NaN;
		for (int valueIndex = 0; valueIndex < lineSize - 1; ++valueIndex) {
			if (Float.isNaN(currentPointX)) {
				LinePoint linePoint = line.getPoints().get(valueIndex);
				currentPointX = chartCalculator.calculateRawX(linePoint.getX());
				currentPointY = chartCalculator.calculateRawY(linePoint.getY());
			}
			if (Float.isNaN(previousPointX)) {
				if (valueIndex > 0) {
					LinePoint linePoint = line.getPoints().get(valueIndex - 1);
					previousPointX = chartCalculator.calculateRawX(linePoint.getX());
					previousPointY = chartCalculator.calculateRawY(linePoint.getY());
				} else {
					previousPointX = currentPointX;
					previousPointY = currentPointY;
				}
			}
			if (Float.isNaN(nextPointX)) {
				LinePoint linePoint = line.getPoints().get(valueIndex + 1);
				nextPointX = chartCalculator.calculateRawX(linePoint.getX());
				nextPointY = chartCalculator.calculateRawY(linePoint.getY());
			}
			// afterNextPoint is always new one or it is equal nextPoint.
			final float afterNextPointX;
			final float afterNextPointY;
			if (valueIndex < lineSize - 2) {
				LinePoint linePoint = line.getPoints().get(valueIndex + 2);
				afterNextPointX = chartCalculator.calculateRawX(linePoint.getX());
				afterNextPointY = chartCalculator.calculateRawY(linePoint.getY());
			} else {
				afterNextPointX = nextPointX;
				afterNextPointY = nextPointY;
			}
			// Calculate control points.
			final float firstDiffX = (nextPointX - previousPointX);
			final float firstDiffY = (nextPointY - previousPointY);
			final float secondDiffX = (afterNextPointX - currentPointX);
			final float secondDiffY = (afterNextPointY - currentPointY);
			final float firstControlPointX = currentPointX + (LINE_SMOOTHNES * firstDiffX);
			final float firstControlPointY = currentPointY + (LINE_SMOOTHNES * firstDiffY);
			final float secondControlPointX = nextPointX - (LINE_SMOOTHNES * secondDiffX);
			final float secondControlPointY = nextPointY - (LINE_SMOOTHNES * secondDiffY);
			// Move to start point.
			if (valueIndex == 0) {
				mLinePath.moveTo(currentPointX, currentPointY);
			}
			mLinePath.cubicTo(firstControlPointX, firstControlPointY, secondControlPointX, secondControlPointY,
					nextPointX, nextPointY);
			// Shift values by one to prevent recalculation of values that have
			// been already calculated.
			previousPointX = currentPointX;
			previousPointY = currentPointY;
			currentPointX = nextPointX;
			currentPointY = nextPointY;
			nextPointX = afterNextPointX;
			nextPointY = afterNextPointY;
		}
		mLinePaint.setColor(line.getColor());
		canvas.drawPath(mLinePath, mLinePaint);
		if (line.isFilled()) {
			drawArea(canvas, line.getAreaTransparency());
		}
	}

	// TODO Drawing points can be done in the same loop as drawing lines but it
	// may cause problems in the future with
	// implementing point styles.
	private void drawPoints(Canvas canvas, Line line, int lineIndex, int mode) {
		final ChartCalculator chartCalculator = mChart.getChartCalculator();
		mPointPaint.setColor(line.getColor());
		int valueIndex = 0;
		for (LinePoint linePoint : line.getPoints()) {
			final float rawValueX = chartCalculator.calculateRawX(linePoint.getX());
			final float rawValueY = chartCalculator.calculateRawY(linePoint.getY());
			if (chartCalculator.isWithinContentRect((int) rawValueX, (int) rawValueY)) {
				// Draw points only if they are within contentRect
				if (MODE_DRAW == mode) {
					canvas.drawCircle(rawValueX, rawValueY, pointRadius, mPointPaint);
					if (line.hasLabels()) {
						drawLabel(canvas, line, linePoint, rawValueX, rawValueY, pointRadius + labelOffset);
					}
				} else if (MODE_HIGHLIGHT == mode) {
					highlightPoint(canvas, line, linePoint, rawValueX, rawValueY, lineIndex, valueIndex);
				} else {
					throw new IllegalStateException("Cannot process points in mode: " + mode);
				}
			}
			++valueIndex;
		}
	}

	private void highlightPoints(Canvas canvas) {
		int lineIndex = mSelectedValue.firstIndex;
		Line line = mChart.getData().lines.get(lineIndex);
		drawPoints(canvas, line, lineIndex, MODE_HIGHLIGHT);
	}

	private void highlightPoint(Canvas canvas, Line line, LinePoint linePoint, float rawValueX, float rawValueY,
			int lineIndex, int valueIndex) {
		if (mSelectedValue.firstIndex == lineIndex && mSelectedValue.secondIndex == valueIndex) {
			mPointPaint.setColor(line.getDarkenColor());
			canvas.drawCircle(rawValueX, rawValueY, pointRadius + touchTolleranceMargin, mPointPaint);
			if (line.hasLabels()) {
				drawLabel(canvas, line, linePoint, rawValueX, rawValueY, pointRadius + labelOffset);
			}
		}
	}

	private void drawLabel(Canvas canvas, Line line, LinePoint linePoint, float rawValueX, float rawValueY, float offset) {
		final ChartCalculator chartCalculator = mChart.getChartCalculator();
		final int nummChars = line.getFormatter().formatValue(labelBuffer, linePoint.getY());
		final float labelWidth = labelPaint.measureText(labelBuffer, labelBuffer.length - nummChars, nummChars);
		final int labelHeight = Math.abs(fontMetrics.ascent);
		float left = rawValueX - labelWidth / 2 - mLabelMargin;
		float right = rawValueX + labelWidth / 2 + mLabelMargin;
		float top = rawValueY - offset - labelHeight - mLabelMargin * 2;
		float bottom = rawValueY - offset;
		if (top < chartCalculator.mContentRect.top) {
			top = rawValueY + offset;
			bottom = rawValueY + offset + labelHeight + mLabelMargin * 2;
		}
		if (left < chartCalculator.mContentRect.left) {
			left = rawValueX;
			right = rawValueX + labelWidth + mLabelMargin * 2;
		}
		if (right > chartCalculator.mContentRect.right) {
			left = rawValueX - labelWidth - mLabelMargin * 2;
			right = rawValueX;
		}
		labelRect.set(left, top, right, bottom);
		int orginColor = labelPaint.getColor();
		labelPaint.setColor(line.getDarkenColor());
		canvas.drawRect(left, top, right, bottom, labelPaint);
		labelPaint.setColor(orginColor);
		canvas.drawText(labelBuffer, labelBuffer.length - nummChars, nummChars, left + mLabelMargin, bottom
				- mLabelMargin, labelPaint);
	}

	private void drawArea(Canvas canvas, int transparency) {
		final ChartCalculator chartCalculator = mChart.getChartCalculator();
		mLinePath.lineTo(chartCalculator.mContentRect.right, chartCalculator.mContentRect.bottom);
		mLinePath.lineTo(chartCalculator.mContentRect.left, chartCalculator.mContentRect.bottom);
		mLinePath.close();
		mLinePaint.setStyle(Paint.Style.FILL);
		mLinePaint.setAlpha(transparency);
		canvas.drawPath(mLinePath, mLinePaint);
		mLinePaint.setStyle(Paint.Style.STROKE);
	}

	private boolean isInArea(float x, float y, float touchX, float touchY, float radius) {
		float diffX = touchX - x;
		float diffY = touchY - y;
		return Math.pow(diffX, 2) + Math.pow(diffY, 2) <= 2 * Math.pow(radius, 2);
	}
}
