package com.opencorrector;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.opencorrector.databinding.ItemSuggestionBinding;
import com.opencorrector.prompt.CorrectionMode;

import java.util.ArrayList;
import java.util.List;

/** Backs the 3-row RecyclerView in the correction popup: one row per {@link CorrectionMode}. */
final class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionsAdapter.ViewHolder> {

    enum State { PENDING, GENERATING, DONE, ERROR, CANCELLED }

    static final class Item {
        final CorrectionMode mode;
        State state = State.PENDING;
        String text = "";
        String errorMessage = null;

        Item(CorrectionMode mode) {
            this.mode = mode;
        }
    }

    interface Listener {
        void onReplaceClicked(int position, String text);
        void onCopyClicked(int position, String text);
    }

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;
    private final boolean readOnly;

    SuggestionsAdapter(Listener listener, boolean readOnly) {
        this.listener = listener;
        this.readOnly = readOnly;
        for (CorrectionMode mode : CorrectionMode.values()) {
            items.add(new Item(mode));
        }
    }

    void updateItem(int position, State state, String text, String errorMessage) {
        Item item = items.get(position);
        item.state = state;
        item.text = text;
        item.errorMessage = errorMessage;
        notifyItemChanged(position);
    }

    Item getItem(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSuggestionBinding binding = ItemSuggestionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSuggestionBinding binding;

        ViewHolder(ItemSuggestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Item item, int position) {
            binding.textModeLabel.setText(item.mode.labelRes);

            boolean generating = item.state == State.GENERATING;
            binding.progressItem.setVisibility(generating ? View.VISIBLE : View.GONE);

            boolean hasError = item.state == State.ERROR;
            binding.textItemError.setVisibility(hasError ? View.VISIBLE : View.GONE);
            if (hasError) {
                binding.textItemError.setText(item.errorMessage);
            }

            binding.textSuggestion.setText(item.text);
            binding.textSuggestion.setVisibility(hasError ? View.GONE : View.VISIBLE);

            boolean canAct = item.state == State.DONE && !item.text.isEmpty();
            binding.buttonReplace.setEnabled(canAct);
            binding.buttonCopy.setEnabled(canAct);
            binding.buttonReplace.setVisibility(readOnly ? View.GONE : View.VISIBLE);

            binding.buttonReplace.setOnClickListener(v -> listener.onReplaceClicked(position, item.text));
            binding.buttonCopy.setOnClickListener(v -> listener.onCopyClicked(position, item.text));
        }
    }
}
