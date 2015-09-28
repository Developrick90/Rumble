/*
 * Copyright (C) 2014 Disrupted Systems
 * This file is part of Rumble.
 * Rumble is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rumble is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.userinterface.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v7.widget.PopupMenu;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.userinterface.activity.DisplayImage;
import org.disrupted.rumble.userinterface.events.UserDeleteStatus;
import org.disrupted.rumble.userinterface.events.UserLikedStatus;
import org.disrupted.rumble.userinterface.events.UserReadStatus;
import org.disrupted.rumble.userinterface.events.UserSavedStatus;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.TimeUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class StatusListAdapter extends BaseAdapter {

    private static final String TAG = "StatusListAdapter";

    private FragmentStatusList fragment;
    private Activity activity;
    private LayoutInflater inflater;
    private List<PushStatus> statuses;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();


    public StatusListAdapter(Activity activity, FragmentStatusList fragment) {
        this.activity = activity;
        this.fragment = fragment;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.statuses = new ArrayList<PushStatus>();
    }

    public void clean() {
        swap(null);
        inflater = null;
        activity = null;
        fragment = null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {

        final PushStatus message = statuses.get(i);

        View statusView = inflater.inflate(R.layout.status_item, null);
        LinearLayout itemInfo   = (LinearLayout) statusView.findViewById(R.id.status_item_info);
        ImageView avatarView    = (ImageView)statusView.findViewById(R.id.status_item_avatar);
        TextView  authorView    = (TextView) statusView.findViewById(R.id.status_item_author);
        TextView  textView      = (TextView) statusView.findViewById(R.id.status_item_body);
        TextView  tocView       = (TextView) statusView.findViewById(R.id.status_item_created);
        TextView  toaView       = (TextView) statusView.findViewById(R.id.status_item_received);
        TextView  groupNameView = (TextView) statusView.findViewById(R.id.status_group_name);
        ImageView attachedView  = (ImageView)statusView.findViewById(R.id.status_item_attached_image);
        ImageView moreView      = (ImageView)statusView.findViewById(R.id.status_item_more_options);
        LinearLayout box        = (LinearLayout)statusView.findViewById(R.id.status_item_box);

        // we draw the avatar
        ColorGenerator generator = ColorGenerator.DEFAULT;
        avatarView.setImageDrawable(
                builder.build(message.getAuthor().getName().substring(0, 1),
                        generator.getColor(message.getAuthor().getUid())));

        // we draw the author field
        authorView.setText(message.getAuthor().getName());
        tocView.setText(TimeUtil.timeElapsed(message.getTimeOfCreation()));
        toaView.setText(TimeUtil.timeElapsed(message.getTimeOfArrival()));
        groupNameView.setText(message.getGroup().getName());
        groupNameView.setTextColor(generator.getColor(message.getGroup().getGid()));


        // we draw the status (with clickable hashtag)
        if (message.getPost().length() == 0) {
            statusView.setVisibility(View.GONE);
        } else {
            SpannableString ss = new SpannableString(message.getPost());
            int beginCharPosition = -1;
            int j;
            for (j = 0; j < message.getPost().length(); j++) {
                if (message.getPost().charAt(j) == '#')
                    beginCharPosition = j;
                if ((message.getPost().charAt(j) == ' ') && (beginCharPosition >= 0)) {
                    final String word = message.getPost().substring(beginCharPosition, j);
                    ClickableSpan clickableSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View textView) {
                            fragment.addFilter(word);
                        }
                    };
                    ss.setSpan(clickableSpan, beginCharPosition, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    beginCharPosition = -1;
                }
            }
            if (beginCharPosition >= 0) {
                final String word = message.getPost().substring(beginCharPosition, j);
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View textView) {
                        fragment.addFilter(word);
                    }
                };
                ss.setSpan(clickableSpan, beginCharPosition, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            textView.setText(ss);
            textView.setMovementMethod(LinkMovementMethod.getInstance());

            // we draw the attached file (if any)
            if (message.hasAttachedFile()) {
                try {
                    File attachedFile = new File(
                            FileUtil.getReadableAlbumStorageDir(),
                            message.getFileName());
                    if (!attachedFile.isFile() || !attachedFile.exists())
                        throw new IOException("file does not exists");

                    Picasso.with(activity)
                            .load("file://"+attachedFile.getAbsolutePath())
                            .resize(96, 96)
                            .centerCrop()
                            .into(attachedView);

                    attachedView.setVisibility(View.VISIBLE);

                    final String name =  message.getFileName();
                    attachedView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Log.d(TAG, "trying to open: " + name);
                            Intent intent = new Intent(activity, DisplayImage.class);
                            intent.putExtra("IMAGE_NAME", name);
                            activity.startActivity(intent);
                        }
                    });


                } catch (IOException ignore) {
                }
            }
            

            moreView.setOnClickListener(new PopupMenuListener(message));
            if (!message.hasUserReadAlready() || (((System.currentTimeMillis() / 1000L) - message.getTimeOfArrival()) < 60)) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    box.setBackgroundDrawable(activity.getResources().getDrawable(R.drawable.status_shape_unread));
                } else {
                    box.setBackground(activity.getResources().getDrawable(R.drawable.status_shape_unread));
                }
                if (!message.hasUserReadAlready()) {
                    message.setUserRead(true);
                    EventBus.getDefault().post(new UserReadStatus(message));
                }
            }
        }

        return statusView;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        if(statuses == null)
            return 0;
        return i;
    }

    @Override
    public int getCount() {
        if(statuses == null)
            return 0;
        return statuses.size();
    }

    public void swap(List<PushStatus> statuses) {
        if(this.statuses != null) {
            for (PushStatus message : this.statuses) {
                message.discard();
                message = null;
            }
            this.statuses.clear();
        }
        if(statuses != null) {
            for (PushStatus message : statuses) {
                this.statuses.add(message);
            }
        }
    }

    public boolean addStatus(PushStatus status) {
        List<PushStatus> newlist = new ArrayList<PushStatus>();
        newlist.add(status);
        for (PushStatus item : statuses) {
            newlist.add(item);
        }
        swap(newlist);
        return true;
    }
    public boolean deleteStatus(String uuid) {
        Iterator<PushStatus> it =statuses.iterator();
        while(it.hasNext()) {
            PushStatus item = it.next();
            if(item.getUuid().equals(uuid)) {
                it.remove();
                return true;
            }
        }
        return false;
    }
    public boolean updateStatus(PushStatus status) {
        Iterator<PushStatus> it =statuses.iterator();
        while(it.hasNext()) {
            PushStatus item = it.next();
            if(item.getUuid().equals(status.getUuid())) {
                item = status;
                return true;
            }
        }
        return false;
    }

    private class PopupMenuListener implements View.OnClickListener
    {

        PushStatus status;
        public PopupMenuListener(PushStatus status) {
            this.status = status;
        }

        @Override
        public void onClick(View v)
        {
            PopupMenu popupMenu =  new PopupMenu(activity, v);
            popupMenu.getMenu().add(Menu.NONE, 1, Menu.NONE, R.string.status_more_option_like);
            popupMenu.getMenu().add(Menu.NONE, 2, Menu.NONE, R.string.status_more_option_save);
            popupMenu.getMenu().add(Menu.NONE, 3, Menu.NONE, R.string.status_more_option_delete);
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    switch (menuItem.getItemId()) {
                        case 1:
                            EventBus.getDefault().post(new UserLikedStatus(status));
                            return true;
                        case 2:
                            EventBus.getDefault().post(new UserSavedStatus(status));
                            return true;
                        case 3:
                            EventBus.getDefault().post(new UserDeleteStatus(status));
                            return true;
                        default:
                            return false;
                    }
                }
            });
            popupMenu.show();
        }

    };

}
