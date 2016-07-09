/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Major revision by David Olsen, 2015
 * Same license.
 */

package com.example.android.viewdragginganimation;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.GridView;

/**
 * The dynamic listview is an extension of listview that supports cell dragging
 * and swapping.
 * <p/>
 * This layout is in charge of positioning the hover cell in the correct location
 * on the screen in response to user touch events. It uses the position of the
 * hover cell to determine when two cells should be swapped. If two cells should
 * be swapped, all the corresponding data mAnimatorSet and layout changes are handled here.
 * <p/>
 * If no cell is selected, all the touch events are passed down to the listview
 * and behave normally. If one of the items in the listview experiences a
 * long press event, the contents of its current visible state are captured as
 * a bitmap and its visibility is set to INVISIBLE. A hover cell is then created and
 * added to this layout as an overlaying BitmapDrawable above the listview. Once the
 * hover cell is translated some distance to signify an item swap, a data mAnimatorSet change
 * accompanied by animation takes place. When the user releases the hover cell,
 * it animates into its corresponding position in the listview.
 * <p/>
 * When the hover cell is either above or below the bounds of the listview, this
 * listview also scrolls on its own so as to reveal additional content.
 */

public class DynamicRecyclingView extends GridView {

    private static final int SMOOTH_SCROLL_AMOUNT_AT_EDGE = 15;
    private static final int MOVE_DURATION = 150;
    private static final int LINE_THICKNESS = 15;

    private float mLastEventX = Float.NaN;
    private float mLastEventY = Float.NaN;

    private boolean mCellIsMobile = false;

    private int mSmoothScrollAmountAtEdge = 0;
    boolean mIsMobileScrolling = false;

    private final int INVALID_ID = -1;
    private long mMobileItemId = INVALID_ID;
    private int mMobileItemPosition = INVALID_ID;
    private View mMobileView;


    private Bitmap mHoverCell;
    private Rect mHoverCellCurrentBounds;
    private Rect mHoverCellOriginalBounds;
    private Paint mHoverCellPaint;

    private final int INVALID_POINTER_ID = -1;
    private int mActivePointerId = INVALID_POINTER_ID;

    private HoverOperation mHoverOperation;

    private AnimatorSet mAnimatorSet = new AnimatorSet();


    public DynamicRecyclingView(Context context) {
        super(context);
        init(context);
    }

    public DynamicRecyclingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public DynamicRecyclingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void init(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mSmoothScrollAmountAtEdge = (int) (SMOOTH_SCROLL_AMOUNT_AT_EDGE * metrics.density);
        setOnScrollListener(mScrollListener);
    }

    /**
     * Draws a black border over the screenshot of the view passed in.
     */
    private Bitmap getBitmapWithBorder(View v) {
        Bitmap bitmap = getBitmapFromView(v);
        Canvas can = new Canvas(bitmap);

        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(LINE_THICKNESS);
        paint.setColor(Color.BLACK);

        can.drawBitmap(bitmap, 0, 0, null);
        can.drawRect(rect, paint);

        return bitmap;
    }

    /**
     * Returns a bitmap showing a screenshot of the view passed in.
     */
    private Bitmap getBitmapFromView(View v) {
        Bitmap bitmap = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        v.draw(canvas);
        return bitmap;
    }


    /**
     * Retrieves the view in the list corresponding to itemID
     */
    public View getViewForID(long itemID) {
        int firstVisiblePosition = getFirstVisiblePosition();
        Adapter adapter = getAdapter();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int position = firstVisiblePosition + i;
            long id = adapter.getItemId(position);
            if (id == itemID) {
                return v;
            }
        }
        return null;
    }

    /**
     * Retrieves the position in the list corresponding to itemID
     */
    public int getPositionForID(long itemID) {
        View v = getViewForID(itemID);
        if (v == null) {
            return -1;
        } else {
            return getPositionForView(v);
        }
    }

    /**
     * Retrieves the position in the list corresponding to given x y coords
     * Ignoring Visibility and potential matrix changes in the childview.
     */
    public int getPositionByPoint(int x, int y) {
        Rect frame = new Rect();
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            frame.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
            if (frame.contains(x, y)) {
                return getFirstVisiblePosition() + i;
            }
        }
        return INVALID_POSITION;
    }

    /**
     * dispatchDraw gets invoked when all the child views are about to be drawn.
     * By overriding this method, the hover cell (BitmapDrawable) can be drawn
     * over the listview's items whenever the listview is redrawn.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mHoverCell != null) {
            canvas.drawBitmap(mHoverCell, mHoverCellCurrentBounds.left, mHoverCellCurrentBounds.top, mHoverCellPaint);
        }
    }

    /**
     * If there is no MobileCell work like normal view.
     * Otherwise process the onTouchEvents.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mCellIsMobile) return super.onTouchEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE:
                int pointerIndex;
                if ((Float.isNaN(mLastEventX)) || Float.isNaN(mLastEventY)) {
                    mActivePointerId = event.getPointerId(0);
                    pointerIndex = event.findPointerIndex(mActivePointerId);
                    mLastEventX = event.getX(pointerIndex);
                    mLastEventY = event.getY(pointerIndex);
                }
                invalidate();

                if (mActivePointerId == INVALID_POINTER_ID) {
                    break;
                }

                pointerIndex = event.findPointerIndex(mActivePointerId);
                float thisEventX = event.getX(pointerIndex);
                float thisEventY = event.getY(pointerIndex);

                float deltaX = thisEventX - mLastEventX;
                float deltaY = thisEventY - mLastEventY;

                if (mCellIsMobile) {
                    mHoverCellCurrentBounds.offsetTo(
                            (int) (mHoverCellCurrentBounds.left + deltaX),
                            (int) (mHoverCellCurrentBounds.top + deltaY));

                    notifyHoverPosition();
                    mIsMobileScrolling = false;
                    handleMobileCellScroll();
                    mLastEventX = (int) thisEventX;
                    mLastEventY = (int) thisEventY;
                }

                break;

            case MotionEvent.ACTION_UP:
                notifyHoverEnded();
                animateDrop();

                resetValues();
                break;
            case MotionEvent.ACTION_CANCEL:
                resetValues();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                /* If a multitouch event took place and the original touch dictating
                 * the movement of the hover cell has ended, then the dragging event
                 * ends and the hover cell is animated to its corresponding position
                 * in the listview. */

                pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                        MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    notifyHoverEnded();
                    animateDrop();
                    resetValues();
                }
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * Catch Changes in the MobileView
     */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        if (mMobileView != null) {
            mMobileView.setVisibility(VISIBLE);
        }
        View oldMobileView = mMobileView;
        mMobileView = getViewForID(mMobileItemId);

        if (mMobileView != null) {
            mMobileItemPosition = getPositionForView(mMobileView);
            mMobileView.setVisibility(INVISIBLE);
        }
        updateOriginalBounds();

        if ((mHoverOperation != null) && (oldMobileView != mMobileView)) {
            mHoverOperation.viewSwitched(this, mMobileItemId, mMobileItemPosition, oldMobileView, mMobileView);
        }
    }


    /**
     * Start the Hover Cell for the given visible ID. To be called externally.
     */
    public boolean startMoveById(long layerId) {
        if (!mCellIsMobile) {

            mMobileItemId = layerId;
            mMobileView = getViewForID(mMobileItemId);
            if (mMobileView == null) return false;
            mMobileItemPosition = getPositionForView(mMobileView);
            mMobileView.setVisibility(INVISIBLE);

            mHoverCell = getBitmapWithBorder(mMobileView);
            updateOriginalBounds();
            mHoverCellCurrentBounds = new Rect(mHoverCellOriginalBounds);
            mHoverCellPaint = new Paint();

            invalidate();

            mCellIsMobile = true;
            return true;
        }
        return false;
    }

    /**
     * Helper to provide a longclick listener.
     */
    public OnItemLongClickListener createOnItemLongClickListener() {
        return new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long id) {
                startMoveById(id);
                return true;
            }
        };
    }

    private void updateOriginalBounds() {
        if (mMobileView == null) {
            mHoverCellOriginalBounds = null;
            return;
        }
        if (mHoverCellOriginalBounds == null)
            mHoverCellOriginalBounds = new Rect(mMobileView.getLeft(), mMobileView.getTop(), mMobileView.getRight(), mMobileView.getBottom());
        else {
            mHoverCellOriginalBounds.set(mMobileView.getLeft(), mMobileView.getTop(), mMobileView.getRight(), mMobileView.getBottom());
        }
    }


    /**
     * Call to set animators and remove translations from all children to return to no translations.
     * All animators set a translation and call this routine to merge.
     */
    private void animateToLocations() {
        //if currently exists, kill the previous animation.
        //This run will finish their operations.
        if (mAnimatorSet.isRunning()) {
            mAnimatorSet.cancel();
        }
        mAnimatorSet = new AnimatorSet();
        //Animate the translated children.
        for (int i = 0, s = getChildCount(); i < s; i++) {
            View v = getChildAt(i);
            if (v.getTranslationX() != 0) {
                Animator animatorX = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0);
                mAnimatorSet.play(animatorX);
            }
            if (v.getTranslationY() != 0) {
                Animator animatorY = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0);
                mAnimatorSet.play(animatorY);
            }
        }
        mAnimatorSet.setDuration(MOVE_DURATION);
        mAnimatorSet.start();
    }

    public void animateDelete(final int deletedIndex) {
        final ViewTreeObserver observer = getViewTreeObserver();
        final int originalFirstVisiblePosition = getFirstVisiblePosition();

        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);

                int firstVisiblePosition = getFirstVisiblePosition();

                int shiftamount = firstVisiblePosition - originalFirstVisiblePosition;

                for (int i = 0; i < getChildCount(); i++) {
                    int position = firstVisiblePosition + i;
                    if (position == deletedIndex) shiftamount++;

                    View v0 = getChildAt(i);
                    if (v0 == null) continue;

                    View v1 = getChildAt((i + shiftamount));
                    if (v1 == null) continue;
                    v0.setTranslationX(v1.getLeft() - v0.getLeft() + v0.getTranslationX());
                    v0.setTranslationY(v1.getTop() - v0.getTop() + v0.getTranslationY());
                }
                animateToLocations();
                return true;
            }
        });
    }

    public void animatePositionShift(final int shiftstart, final int shiftend, final int shiftamount) {
        final ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);

                int firstVisiblePosition = getFirstVisiblePosition();

                int start = Math.max(shiftstart - firstVisiblePosition, 0);
                int end = Math.min(shiftend - firstVisiblePosition, getChildCount() - 1);
                for (int i = start; i <= end; i++) {
                    View v0 = getChildAt(i);
                    if (v0 == null) continue;

                    View v1 = getChildAt((i - shiftamount));
                    if (v1 == null) continue;

                    v0.setTranslationX(v1.getLeft() - v0.getLeft() + v0.getTranslationX());
                    v0.setTranslationY(v1.getTop() - v0.getTop() + v0.getTranslationY());
                }
                animateToLocations();
                return true;
            }
        });
    }

    public void animatePositionMove(final int... animatePositions) {
        final ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);
                int firstVisiblePosition = getFirstVisiblePosition();
                for (int i = 0, s = animatePositions.length; i < s; i += 2) {
                    int p0 = animatePositions[i];
                    int p1 = animatePositions[i + 1];

                    View v0 = getChildAt(p0 - firstVisiblePosition);
                    if (v0 == null) continue;

                    View v1 = getChildAt(p1 - firstVisiblePosition);
                    if (v1 == null) continue;

                    v0.setTranslationX(v1.getLeft() - v0.getLeft() + v0.getTranslationX());
                    v0.setTranslationY(v1.getTop() - v0.getTop() + v0.getTranslationY());
                }
                animateToLocations();
                return true;
            }
        });
    }

    public void animateItem(final long id, final float left, final float top) {
        final ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);
                View animateview = getViewForID(id);
                if (animateview != null) {
                    animateview.setTranslationX((left - animateview.getLeft()) + animateview.getTranslationX());
                    animateview.setTranslationY((top - animateview.getTop()) + animateview.getTranslationY());
                    animateToLocations();
                }
                return true;
            }
        });
    }

    /**
     * Internal animation for dropped hovercell to fit into place.
     */
    private void animateDrop() {
        animateItem(mMobileItemId, mHoverCellCurrentBounds.left, mHoverCellCurrentBounds.top);
    }

    /**
     * Resets all the appropriate fields to a default state
     */
    private void resetValues() {
        mCellIsMobile = false;
        mHoverCellCurrentBounds = null;
        if (mMobileView != null) {
            mMobileView.setVisibility(VISIBLE);
        }
        mMobileView = null;
        mMobileItemId = INVALID_ID;
        mHoverCell = null;
        mIsMobileScrolling = false;
        mLastEventX = Float.NaN;
        mLastEventY = Float.NaN;
        invalidate();
    }


    /**
     * Changes the alpha of the hovercell paint.
     */

    public void setHoverCellAlpha(int alpha) {
        mHoverCellPaint.setAlpha(alpha);
    }


    private void notifyPositionViewFrameChange(int currentStart, int currentEnd, int previousStart, int previousEnd) {
        if (mCellIsMobile) {
            boolean inRange = (mMobileItemPosition >= currentStart) && (mMobileItemPosition <= currentEnd);
            if (mMobileView != null) {
                if (!inRange) {
                    mMobileView.setVisibility(VISIBLE);
                    if (mHoverOperation != null) {
                        mHoverOperation.viewSwitched(this, mMobileItemId, mMobileItemPosition, mMobileView, null);
                    }
                    mMobileView = null;
                }
                updateOriginalBounds();
            } else {
                if (inRange) {
                    mMobileView = getViewForID(mMobileItemId);
                    if (mMobileView == null) {
                        //should not happen.
                        return;
                    }
                    mMobileView.setVisibility(INVISIBLE);
                    if (mHoverOperation != null) {
                        mHoverOperation.viewSwitched(this, mMobileItemId, mMobileItemPosition, null, mMobileView);
                    }
                }
                updateOriginalBounds();
            }
            notifyHoverPosition();
        }

    }

    /**
     * Notify the HoverCellEvent holder so it can deal with dynamic changes.
     * Calls when the hovercell is dropped.
     */

    private void notifyHoverEnded() {
        if (mHoverOperation != null) {
            int position = getPositionByPoint(mHoverCellCurrentBounds.centerX(), mHoverCellCurrentBounds.centerY());
            mHoverOperation.hoverEnded(DynamicRecyclingView.this, mMobileItemId, position, mMobileItemPosition, mHoverCellCurrentBounds, mHoverCellOriginalBounds);
        }
    }

    /**
     * Notify the HoverCellEvent holder so it can deal with dynamic changes.
     * Called when the hovercell is moved.
     */
    private void notifyHoverPosition() {
        //dynamicListView.getPositionByPoint(hoverCellBounds.centerX(), hoverCellBounds.centerY());
        if (mHoverOperation != null) {
            int position = getPositionByPoint(mHoverCellCurrentBounds.centerX(), mHoverCellCurrentBounds.centerY());
            mHoverOperation.hoverPosition(DynamicRecyclingView.this, mMobileItemId, position, mMobileItemPosition, mHoverCellCurrentBounds, mHoverCellOriginalBounds);
        }
    }

    public HoverOperation getHoverOperation() {
        return mHoverOperation;
    }

    public void setHoverOperation(HoverOperation hoverOp) {
        this.mHoverOperation = hoverOp;
    }


    /**
     * Handles scrolling of the hovercell.
     */
    private void handleMobileCellScroll() {
        if (mHoverCellCurrentBounds == null) return;
        int offset = computeVerticalScrollOffset();
        int height = getHeight();
        int extent = computeVerticalScrollExtent();
        int range = computeVerticalScrollRange();
        int hoverViewTop = mHoverCellCurrentBounds.top;
        int hoverHeight = mHoverCellCurrentBounds.height();
        if (hoverViewTop <= 0 && offset > 0) {
            smoothScrollBy(-mSmoothScrollAmountAtEdge, 0);
            mIsMobileScrolling = true;
        } else if (hoverViewTop + hoverHeight >= height && (offset + extent) < range) {
            smoothScrollBy(mSmoothScrollAmountAtEdge, 0);
            mIsMobileScrolling = true;
        }
    }

    private AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {

        private int mPreviousFirstVisibleItem = -1;
        private int mPreviousVisibleItemCount = -1;
        private int mCurrentFirstVisibleItem;
        private int mCurrentVisibleItemCount;

        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                             int totalItemCount) {
            mCurrentFirstVisibleItem = firstVisibleItem;
            mCurrentVisibleItemCount = visibleItemCount;

            mPreviousFirstVisibleItem = (mPreviousFirstVisibleItem == -1) ? mCurrentFirstVisibleItem
                    : mPreviousFirstVisibleItem;
            mPreviousVisibleItemCount = (mPreviousVisibleItemCount == -1) ? mCurrentVisibleItemCount
                    : mPreviousVisibleItemCount;
            int currentLastVisibleItem = mCurrentFirstVisibleItem + mCurrentVisibleItemCount;
            int previousLastVisibleItem = mPreviousFirstVisibleItem + mPreviousVisibleItemCount;
            if ((mCurrentFirstVisibleItem != mPreviousFirstVisibleItem) || (currentLastVisibleItem != previousLastVisibleItem)) {
                notifyPositionViewFrameChange(mCurrentFirstVisibleItem, currentLastVisibleItem, mPreviousFirstVisibleItem, previousLastVisibleItem);
            }
            mPreviousFirstVisibleItem = mCurrentFirstVisibleItem;
            mPreviousVisibleItemCount = mCurrentVisibleItemCount;
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if ((scrollState == SCROLL_STATE_IDLE) && (mCurrentVisibleItemCount > 0)) {
                if (mCellIsMobile && mIsMobileScrolling) {
                    handleMobileCellScroll();
                }
            }
        }
    };

}

