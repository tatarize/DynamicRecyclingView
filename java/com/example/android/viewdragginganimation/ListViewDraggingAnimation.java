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

package com.example.android.viewdragginganimation;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;

import java.util.ArrayList;

/**
 * This application creates a listview where the ordering of the data set
 * can be modified in response to user touch events.
 * <p/>
 * An item in the listview is selected via a long press event and is then
 * moved around by tracking and following the movement of the user's finger.
 * When the item is released, it animates to its new position within the listview.
 */


public class ListViewDraggingAnimation extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_view);

        final ArrayList<String> mCheeseList = new ArrayList<>();

        for (int i = 0; i < Cheeses.sCheeseStrings.length; ++i) {
            mCheeseList.add(Cheeses.sCheeseStrings[i]);
        }


        final DynamicRecyclingView listView = (DynamicRecyclingView) findViewById(R.id.listview);

        //listView.setOnItemLongClickListener(listView.createOnItemLongClickListener());

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.text_view, mCheeseList) {

            @Override
            public long getItemId(int position) {
                try {
                    return mCheeseList.get(position).hashCode();
                } catch (IndexOutOfBoundsException e) {
                    return -1;
                }
            }

            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                //this is klugy for the example, you can call startMoveById from any code.
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listView.startMoveById(getItemId(position));
                    }
                });
                return view;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGridOperation);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rB0:
                        listView.setHoverOperation(new HoverOpertationDropSwap(mCheeseList));
                        break;
                    case R.id.rB1:
                        listView.setHoverOperation(new HoverOperationAllSwap(mCheeseList));
                        break;
                    case R.id.rB2:
                        listView.setHoverOperation(new HoverOperationInsert(mCheeseList));
                        break;
                    case R.id.rB3:
                        listView.setHoverOperation(null);
                        break;
                }
            }
        });

        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }
}
