package xyz.andoroid.timecounter;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;
import com.jakewharton.threetenabp.AndroidThreeTen;

import org.threeten.bp.DayOfWeek;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;

import xyz.andoroid.timecounter.model.*;

public class MainActivity extends AppCompatActivity {
    private LocalDateTime now;

    private List<Triplet> events;

    private LocalDateTime startOfWeek;
    private NotificationUtils notificationUtils;
    private SharedPreferences preferences;

    private MusicUtils musicUtils;

    private boolean isLongPressed = false;
    private int deltaOnLongPress = 1000;

    private int lastIndex = -1;

    private boolean weekEnded = false;

    private boolean dormitorySwitchJustChanged = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);
        setContentView(R.layout.activity_main);
        now = LocalDateTime.now();
        events = new ArrayList<>();
        notificationUtils = new NotificationUtils((NotificationManager)getSystemService(NOTIFICATION_SERVICE), this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        startOfWeek = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 0,0);
        startOfWeek = startOfWeek.minusDays(now.getDayOfWeek().compareTo(DayOfWeek.MONDAY));



        musicUtils = new MusicUtils(this, R.raw.ring);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final TextView eventName = findViewById(R.id.EventName);
        final TextView eventTime = findViewById(R.id.EventTime);
        final TextView nextEvent = findViewById(R.id.NextEvent);
        final ConstraintLayout constraintLayout = findViewById(R.id.MainLayout);
        constraintLayout.setOnClickListener(v -> v.setBackgroundColor((int)(Math.random()*0xFF000000)));
        constraintLayout.setOnLongClickListener(v -> {
            isLongPressed = true;
            return true;
        });
        constraintLayout.setOnTouchListener((v,event) -> {
                v.onTouchEvent(event);
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(isLongPressed) isLongPressed = false;
                }
                return false;
        });

        String fontFromPrefs = preferences.getString("font","tnm");
        if(fontFromPrefs.equalsIgnoreCase("tnm")) {
            eventName.setTypeface(ResourcesCompat.getFont(this, R.font.times));
            eventTime.setTypeface(ResourcesCompat.getFont(this, R.font.times));
            nextEvent.setTypeface(ResourcesCompat.getFont(this, R.font.times));
        } else if(fontFromPrefs.equalsIgnoreCase("anime_ace")) {
            eventName.setTypeface(ResourcesCompat.getFont(this, R.font.anime_ace));
            eventTime.setTypeface(ResourcesCompat.getFont(this, R.font.anime_ace));
            nextEvent.setTypeface(ResourcesCompat.getFont(this, R.font.anime_ace));
        }
        if(preferences.getBoolean("epilepticBG", false)) {
            final Thread bgChangingThread = new Thread() {
                @Override
                public void run() {
                    try {
                        while (!isInterrupted()) {
                            Thread.sleep(deltaOnLongPress);
                            runOnUiThread(() -> {
                                if (isLongPressed) {
                                    constraintLayout.setBackgroundColor((int) (Math.random() * 0xFF000000));
                                    if (deltaOnLongPress > 10)
                                        deltaOnLongPress /= 1.3;
                                } else {
                                    deltaOnLongPress = 1000;
                                }
                            });
                        }
                    } catch (InterruptedException ignored) {
                    }
                }
            };
            bgChangingThread.start();
        }

        ReaderUtils qb = new ReaderUtils(this);
        List<String> allTextLines = qb.readLine(preferences.getString("class","a11_2020")+".csv");
        String[] ss;
        Triplet pr;
        for(String s : allTextLines) {
            ss = s.split(",");
            pr = new Triplet(ss[0], Long.parseLong(ss[1]), Integer.parseInt(ss[2]));
            events.add(pr);
        }

        final Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(() -> {
                            if(weekEnded) {
                                startOfWeek = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 0,0);
                                startOfWeek = startOfWeek.minusDays(now.getDayOfWeek().compareTo(DayOfWeek.MONDAY));
                                weekEnded = false;
                            }
                            long secsBetweenNowAndStart = ChronoUnit.SECONDS.between(startOfWeek, now);
                            int index = -1;
                            for(int i = 0;i<events.size();i++) {
                                if(!preferences.getBoolean("showDormitory", false) && events.get(i).third == 0  ) continue;
                                if(secsBetweenNowAndStart > events.get(i).second) continue;
                                index = i;
                                break;
                            }
                            if(index != -1) {
                                eventName.setText(events.get(index).first);
                                long s = events.get(index).second-secsBetweenNowAndStart;
                                eventTime.setText(TimeUtils.convertFromSeconds(s,true));
                                if(lastIndex == -1) lastIndex = index;
                                if(index != lastIndex && preferences.getBoolean("ring",true)) {
                                    if(dormitorySwitchJustChanged) dormitorySwitchJustChanged = false;
                                    else musicUtils.play(5000);
                                    lastIndex = index;
                                }
                                notificationUtils.showNotification(0, events.get(index).first, TimeUtils.convertFromSeconds(s,true));

                                StringBuilder next = new StringBuilder();
                                int t = 11;
                                for(int i=index+1;i<index+t && i<events.size();i++) {
                                    if(!preferences.getBoolean("showDormitory", false) && events.get(i).third == 0) {t++;continue;}
                                    next.append(events.get(i).first).append(" ").append(TimeUtils.convertFromSeconds(events.get(i).second - secsBetweenNowAndStart, false)).append("\n");
                                }

                                nextEvent.setText(next);
                            }
                            else {
                                eventName.setText(R.string.no_further_events);
                                nextEvent.setText("");
                                long s = 7*24*60*60-secsBetweenNowAndStart;
                                eventTime.setText(TimeUtils.convertFromSeconds(s,true));
                                weekEnded = true;
                            }


                            now = LocalDateTime.now();
                        });
                    }
                } catch (InterruptedException ignored) {}
            }
        };

        thread.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.settings_item) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
