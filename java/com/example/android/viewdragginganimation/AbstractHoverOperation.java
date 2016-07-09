package com.example.android.viewdragginganimation;

import android.graphics.Rect;
import android.view.View;

import java.util.ArrayList;

public abstract class AbstractHoverOperation implements HoverOperation {

    @Override
    public void hoverEnded(DynamicRecyclingView dynamicListView, long stableID, int currentPosition, int originalPosition, Rect hoverCellBounds, Rect viewBounds) {

    }

    @Override
    public void hoverPosition(DynamicRecyclingView dynamicListView, long stableID, int currentPosition, int originalPosition, Rect hoverCellBounds, Rect viewBounds) {

    }

    @Override
    public void viewSwitched(DynamicRecyclingView dynamicListView, long stableID, int position, View oldView, View newView) {

    }

    public void swapElements(ArrayList arrayList, int indexOne, int indexTwo) {
        try {
            Object temp1 = arrayList.get(indexOne);
            Object temp2 = arrayList.get(indexTwo);

            arrayList.set(indexOne, temp2);
            arrayList.set(indexTwo, temp1);
        } catch (IndexOutOfBoundsException e) {
        }
    }

    public void deleteElement(ArrayList arrayList, int index) {
        try {
            arrayList.remove(index);
        } catch (IndexOutOfBoundsException e) {
        }
    }

    public void moveElement(ArrayList arrayList, int fromIndex, int toIndex) {
        try {
            Object temp1 = arrayList.get(fromIndex);
            if (fromIndex == toIndex) return;

            arrayList.remove(fromIndex);
            arrayList.add(toIndex, temp1);

        } catch (IndexOutOfBoundsException e) {
        }
    }
}