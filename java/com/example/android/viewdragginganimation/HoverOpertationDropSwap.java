package com.example.android.viewdragginganimation;

import android.graphics.Rect;
import android.widget.BaseAdapter;

import java.util.ArrayList;


public class HoverOpertationDropSwap extends AbstractHoverOperation {
    ArrayList backingList;

    public HoverOpertationDropSwap(ArrayList backingList) {
        this.backingList = backingList;
    }

    @Override
    public void hoverEnded(DynamicRecyclingView dynamicListView, long stableID, int currentPosition, int originalPosition, Rect hoverCellBounds, Rect viewBounds) {
        if (currentPosition == DynamicRecyclingView.INVALID_POSITION) {
            deleteElement(backingList,originalPosition);
            dynamicListView.animateDelete(originalPosition);
            ((BaseAdapter) dynamicListView.getAdapter()).notifyDataSetChanged();
            return;
        }
        if (currentPosition != originalPosition) {
            dynamicListView.animatePositionMove(originalPosition, currentPosition);
            swapElements(backingList, originalPosition, currentPosition);
            ((BaseAdapter) dynamicListView.getAdapter()).notifyDataSetChanged();
        }
    }

}
