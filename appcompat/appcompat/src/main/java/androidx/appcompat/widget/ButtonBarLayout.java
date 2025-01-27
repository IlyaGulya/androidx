/*
 * Copyright (C) 2015 The Android Open Source Project
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
package androidx.appcompat.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.RestrictTo;
import androidx.appcompat.R;
import androidx.core.view.ViewCompat;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * An extension of LinearLayout that automatically switches to vertical
 * orientation when it can't fit its child views horizontally.
 *
 */
@RestrictTo(LIBRARY_GROUP_PREFIX)
public class ButtonBarLayout extends LinearLayout {
    /** Amount of the second button to "peek" above the fold when stacked. */
    private static final int PEEK_BUTTON_DP = 16;

    /** Whether the current configuration allows stacking. */
    private boolean mAllowStacking;

    /** Whether the button bar is currently stacked. */
    private boolean mStacked;

    private int mLastWidthSize = -1;

    public ButtonBarLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        final TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ButtonBarLayout);
        ViewCompat.saveAttributeDataForStyleable(this, context, R.styleable.ButtonBarLayout,
                attrs, ta, 0, 0);
        mAllowStacking = ta.getBoolean(R.styleable.ButtonBarLayout_allowStacking, true);
        ta.recycle();

        // Stacking may have already been set implicitly via orientation="vertical", in which
        // case we'll need to validate it against allowStacking and re-apply explicitly.
        if (getOrientation() == LinearLayout.VERTICAL) {
            setStacked(mAllowStacking);
        }
    }

    public void setAllowStacking(boolean allowStacking) {
        if (mAllowStacking != allowStacking) {
            mAllowStacking = allowStacking;
            if (!mAllowStacking && isStacked()) {
                setStacked(false);
            }
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (mAllowStacking) {
            if (widthSize > mLastWidthSize && isStacked()) {
                // We're being measured wider this time, try un-stacking.
                setStacked(false);
            }

            mLastWidthSize = widthSize;
        }

        boolean needsRemeasure = false;

        // If we're not stacked, make sure the measure spec is AT_MOST rather
        // than EXACTLY. This ensures that we'll still get TOO_SMALL so that we
        // know to stack the buttons.
        final int initialWidthMeasureSpec;
        if (!isStacked() && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            initialWidthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.AT_MOST);

            // We'll need to remeasure again to fill excess space.
            needsRemeasure = true;
        } else {
            initialWidthMeasureSpec = widthMeasureSpec;
        }

        super.onMeasure(initialWidthMeasureSpec, heightMeasureSpec);

        if (mAllowStacking && !isStacked()) {
            final boolean stack;

            final int measuredWidth = getMeasuredWidthAndState();
            final int measuredWidthState = measuredWidth & View.MEASURED_STATE_MASK;
            stack = measuredWidthState == View.MEASURED_STATE_TOO_SMALL;

            if (stack) {
                setStacked(true);
                // Measure again in the new orientation.
                needsRemeasure = true;
            }
        }

        if (needsRemeasure) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        // Compute minimum height such that, when stacked, some portion of the
        // second button is visible.
        int minHeight = 0;
        final int firstVisible = getNextVisibleChildIndex(0);
        if (firstVisible >= 0) {
            final View firstButton = getChildAt(firstVisible);
            final LayoutParams firstParams = (LayoutParams) firstButton.getLayoutParams();
            minHeight += getPaddingTop() + firstButton.getMeasuredHeight()
                    + firstParams.topMargin + firstParams.bottomMargin;
            if (isStacked()) {
                final int secondVisible = getNextVisibleChildIndex(firstVisible + 1);
                if (secondVisible >= 0) {
                    minHeight += getChildAt(secondVisible).getPaddingTop()
                            + (int) (PEEK_BUTTON_DP * getResources().getDisplayMetrics().density);
                }
            } else {
                minHeight += getPaddingBottom();
            }
        }

        if (ViewCompat.getMinimumHeight(this) != minHeight) {
            setMinimumHeight(minHeight);

            // Re-measure immediately to fill excess space.
            if (heightMeasureSpec == MeasureSpec.UNSPECIFIED) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    private int getNextVisibleChildIndex(int index) {
        for (int i = index, count = getChildCount(); i < count; i++) {
            if (getChildAt(i).getVisibility() == View.VISIBLE) {
                return i;
            }
        }
        return -1;
    }

    private void setStacked(boolean stacked) {
        if (mStacked != stacked && (!stacked || mAllowStacking)) {
            mStacked = stacked;

            setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            setGravity(stacked ? Gravity.END : Gravity.BOTTOM);

            final View spacer = findViewById(R.id.spacer);
            if (spacer != null) {
                spacer.setVisibility(stacked ? View.GONE : View.INVISIBLE);
            }

            // Reverse the child order. This is specific to the Material button
            // bar's layout XML and will probably not generalize.
            final int childCount = getChildCount();
            for (int i = childCount - 2; i >= 0; i--) {
                bringChildToFront(getChildAt(i));
            }
        }
    }

    private boolean isStacked() {
        return mStacked;
    }
}
