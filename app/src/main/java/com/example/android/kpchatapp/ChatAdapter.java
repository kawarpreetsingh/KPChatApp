package com.example.android.kpchatapp;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

/**
 * Created by Kawarpreet Singh on 05-09-2017.
 * Used by RecyclerView to deal with each itemViews in the form of ViewHolders.
 */

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    List<Message> messageList;
    Context context;

    ChatAdapter(Context context, List<Message> messageList) {
        this.context = context;
        this.messageList = messageList;
    }

    @Override
    public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.layout_item, parent, false);
        return new ChatViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ChatViewHolder holder, int position) {
        Message message = messageList.get(position);
        //When photoUrl is null not display photo but display text
        if (message.getPhotoUrl() == null) {
            holder.photoImageView.setVisibility(View.GONE);
            holder.messageTextView.setVisibility(View.VISIBLE);
            holder.messageTextView.setText(message.getText());
            //when photoUrl is not null display photo but not text
        } else {
            holder.photoImageView.setVisibility(View.VISIBLE);
            holder.messageTextView.setVisibility(View.GONE);
            Glide.with(context).load(message.getPhotoUrl()).into(holder.photoImageView);
        }
        holder.nameTextView.setText(message.getName());
    }

    @Override
    public int getItemCount() {
        if (messageList != null && messageList.size() > 0) {
            return messageList.size();
        } else {
            return 0;
        }
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, messageTextView;
        ImageView photoImageView;

        public ChatViewHolder(View itemView) {
            super(itemView);
            nameTextView = (TextView) itemView.findViewById(R.id.name_text_view);
            messageTextView = (TextView) itemView.findViewById(R.id.message_text_view);
            photoImageView = (ImageView) itemView.findViewById(R.id.photo_image_view);
        }
    }
}
