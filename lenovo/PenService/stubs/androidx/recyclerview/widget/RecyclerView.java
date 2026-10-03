/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.recyclerview.widget;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class RecyclerView extends ViewGroup {
    public RecyclerView(Context context) {
        super(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {}

    public Adapter getAdapter() {
        throw new RuntimeException("stub");
    }

    public int getChildAdapterPosition(View child) {
        throw new RuntimeException("stub");
    }

    public void addItemDecoration(ItemDecoration decor) {}

    public void invalidateItemDecorations() {}

    public boolean isComputingLayout() {
        throw new RuntimeException("stub");
    }

    public static class ViewHolder {
        public final View itemView;

        public ViewHolder(View itemView) {
            this.itemView = itemView;
        }
    }

    public abstract static class Adapter<VH extends ViewHolder> {
        public abstract int getItemCount();

        public void registerAdapterDataObserver(AdapterDataObserver observer) {}
    }

    public abstract static class AdapterDataObserver {
        public void onChanged() {}

        public void onItemRangeChanged(int positionStart, int itemCount) {}

        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {}

        public void onItemRangeInserted(int positionStart, int itemCount) {}

        public void onItemRangeRemoved(int positionStart, int itemCount) {}

        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {}
    }

    public abstract static class ItemDecoration {
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, State state) {}
    }

    public static class State {}
}
