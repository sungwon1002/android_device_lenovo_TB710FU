/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Palm rejection of the touch controller, driven through the Lenovo
 * touchscreen HAL the way the stock ZUI input service does it:
 *
 * - "Big palm" mode (setBigPalm, /proc/game_mode): 1 rejects large palm
 *   contacts, 2 is the game profile, 0 is normal. Stock sets 1 while a known
 *   writing or drawing app (or one added in the pen settings) is in the
 *   foreground and 2 for games. With "dynamic palm rejection" on it also
 *   switches to 1 as soon as the stylus touches the screen in any other app,
 *   until the foreground app changes.
 * - Edge inhibition (setEdgeInhibition, /proc/panel_direction): the touch
 *   controller rejects touches along the edges of the current orientation, so
 *   it is told every display rotation.
 */
final class PalmController {
    private static final String TAG = "TB520FUPalm";

    private static final int MODE_NORMAL = 0;
    private static final int MODE_WRITING = 1;
    private static final int MODE_GAME = 2;

    /** Settings written by the stock pen settings (PenService). */
    private static final String SETTING_DYNAMIC = "pen_dynamic_palm_rejection";
    private static final String SETTING_CUSTOM = "pen_palm_rejection_custom_packages";
    /** 1 while a writing app is in front; read by the Lenovo pen apps. */
    private static final String SETTING_WRITING = "lenovo_pen_writing";

    /** Stock framework-res config_stylus_pen_bigpalm_packages. */
    private static final Set<String> WRITING_APPS = new HashSet<>(Arrays.asList(
            "net.huanci.hsjpro", "kr.neolab.neonote", "jp.co.celsys.clipstudiopaint.googleplay",
            "easynotes.notes.notepad.notebook.privatenotes.note", "com.ZWSoft.ZWCAD",
            "com.zui.notes", "com.youdao.note", "com.xodo.pdf.reader", "com.wacom.signpropdf",
            "com.wacom.bamboopapertab", "com.vson.edraw", "com.vistrav.whiteboard",
            "com.ttluoshi.drawapp", "com.tophatch.concepts.china", "com.tophatch.concepts",
            "com.steadfastinnovation.android.projectpapyrus", "com.sonymobile.sketch",
            "com.newskyer.draw", "com.myscript.nebo", "com.myscript.calculator", "com.mubu.app",
            "com.microsoft.office.onenote", "com.medibang.android.paint.tablet",
            "com.medibang.android.jumppaint", "com.liubowang.drawingboard",
            "com.kdanmobile.android.pdfreader.google.pad", "com.jideos.jnotes",
            "com.infraware.office.link", "com.hlg.daydaytobusiness", "com.google.android.keep",
            "com.goodnotepad.smarttodolistnote", "com.foxit.mobile.pdf.lite",
            "com.flexcil.flexcilnote", "com.eyewind.paperone", "com.yinxiang", "com.evernote",
            "com.dencreak.esmemo", "com.color365.sketch", "com.brakefield.painter",
            "com.brakefield.idfree", "com.axis.drawingdesk.v3", "com.adsk.sketchbook",
            "com.pick.sketchbook", "cn.wps.moffice_eng", "cn.canva.editor",
            "jp.ne.ibis.ibispaintx.app", "com.tayasui.sketches", "jp.ne.ibis.ibispaint.app",
            "com.picsart.draw", "com.bytestorm.artflow", "com.topstack.kilonotes.pad",
            "com.delgeo.desygner", "com.orion.notein", "com.microsoft.office.officehub",
            "com.adobe.reader", "com.officedocument.word.docx.document.viewer",
            "com.officetoolsapps.document.reader.editor", "com.adobe.fas",
            "com.penly.planner.notes.tasks.reminders", "com.fluidtouch.noteshelf2",
            "com.viettran.INKrediblePro", "com.acadoid.lecturenotes", "com.zoho.notebook",
            "note.notepad.todo.notebook", "io.notewise.app", "com.yocto.wenote",
            "com.kreoSoft.Notepad", "com.cga.my.color.note.notepad", "note.notepad.diary.notebook",
            "com.litewhite.notemaster.free", "org.mightyfrog.android.simplenotepad",
            "evansir.securenotepad", "com.eyewind.paperonefree", "org.gimp.GIMP",
            "org.hermit.simpledrawpro", "drawsketch.com.sketchpad.drawing.painting",
            "org.n0n3m4.nomad", "drawingapps.drawingsketchpad.paintingtools",
            "com.royole.rydrawing", "com.fiistudio.fiinote", "com.nineton.shouzhang",
            "com.chenupt.text", "com.luyun.lightnote", "com.videocutting.editor",
            "com.sygd.gdpaintfive", "jp.ne.ibis.ibispaintx.app.hms", "net.huanci.hsj",
            "com.moonlight.huatu", "com.wyart.setfourpaper", "com.zq.hand_drawn",
            "com.littlez.draw", "com.zh.drawworld", "com.lloo.w.painting", "com.painting.draw",
            "com.google.android.apps.docs", "com.google.android.apps.photos",
            "com.jideos.jnotes.overseas.google", "com.lenovo.styluspen",
            "com.myscript.calculator.lenovo", "com.myscript.nebo.lenovo", "com.viettran.INKredible"));

    /** Stock framework-res config_stylus_pen_game_packages. */
    private static final Set<String> GAME_APPS = new HashSet<>(Arrays.asList(
            "com.tencent.tmgp.pubgmhd", "com.tencent.lolm", "com.tencent.jkchess",
            "com.miHoYo.Yuanshen", "com.tencent.KiHan", "cn.jj", "com.tencent.tmgp.cf",
            "com.nianticlabs.pokemongo", "com.tencent.tmgp.sgame",
            "com.happyelements.AndroidAnimal", "com.minitech.miniworld.TMobile.Lenovo",
            "com.aligames.kuang.kybc.lenovo", "com.wepie.snakegame.lenovo",
            "com.netease.harrypotter", "com.tencent.tmgp.speedmobile", "com.ztgame.bob",
            "com.google.android.play.games", "com.dts.freefireth", "com.roblox.client",
            "com.supercell.brawlstars", "com.innersloth.spacemafia", "com.mojang.minecraftpe",
            "com.tocaboca.tocalifeworld", "com.topwar.gp", "com.noodlecake.altosodyssey",
            "com.riotgames.league.wildrift", "com.tencent.ig"));

    private final Context mContext;
    private final Handler mHandler;

    private boolean mDynamic;
    private String mCustom = "";
    private String mForeground;
    /** Mode for the foreground app, before dynamic rejection. */
    private int mAppMode = MODE_NORMAL;
    private int mMode = -1;
    private int mRotation = -1;

    private final Runnable mCheckForeground = Safe.run("palm foreground", this::checkForeground);
    private final Runnable mDynamicOn = Safe.run("palm dynamic", this::applyDynamic);

    PalmController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        ContentResolver cr = mContext.getContentResolver();
        ContentObserver observer = Safe.observer(mHandler, "palm settings", uri -> {
            readSettings();
            mForeground = null;
            checkForeground();
        });
        cr.registerContentObserver(Settings.System.getUriFor(SETTING_DYNAMIC), false, observer);
        cr.registerContentObserver(Settings.System.getUriFor(SETTING_CUSTOM), false, observer);
        readSettings();

        try {
            ActivityTaskManager.getService().registerTaskStackListener(new TaskStackListener() {
                @Override
                public void onTaskStackChanged() {
                    // binder thread: debounce onto our handler
                    mHandler.removeCallbacks(mCheckForeground);
                    mHandler.postDelayed(mCheckForeground, 100);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "registerTaskStackListener", e);
        }

        mContext.getSystemService(DisplayManager.class).registerDisplayListener(
                new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int displayId) {}

                    @Override
                    public void onDisplayRemoved(int displayId) {}

                    @Override
                    public void onDisplayChanged(int displayId) {
                        if (displayId == Display.DEFAULT_DISPLAY) {
                            Safe.run("palm rotation", () -> updateRotation(false)).run();
                        }
                    }
                }, mHandler);

        // The driver restores both values after resume, but the HAL may have
        // restarted meanwhile; set them again when the screen comes on.
        mContext.registerReceiver(Safe.receiver("palm screen on", (c, i) -> {
            updateRotation(true);
            setMode(mMode, true);
        }), new IntentFilter(Intent.ACTION_SCREEN_ON), null, mHandler);

        updateRotation(true);
        checkForeground();
    }

    /** Called on our handler for every stylus ACTION_DOWN. */
    void onStylusDown() {
        if (!mDynamic || mAppMode != MODE_NORMAL || mMode == MODE_WRITING) return;
        // Same 100 ms debounce as stock.
        mHandler.removeCallbacks(mDynamicOn);
        mHandler.postDelayed(mDynamicOn, 100);
    }

    private void readSettings() {
        ContentResolver cr = mContext.getContentResolver();
        mDynamic = Settings.System.getInt(cr, SETTING_DYNAMIC, 0) == 1;
        String custom = Settings.System.getString(cr, SETTING_CUSTOM);
        mCustom = custom == null ? "" : custom;
    }

    private boolean isWritingApp(String pkg) {
        // Stock matches the custom list with a plain contains().
        return WRITING_APPS.contains(pkg) || (!TextUtils.isEmpty(mCustom) && mCustom.contains(pkg));
    }

    private void checkForeground() {
        String pkg = null;
        try {
            ActivityTaskManager.RootTaskInfo info =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            if (info != null && info.topActivity != null) pkg = info.topActivity.getPackageName();
        } catch (Exception e) {
            Log.w(TAG, "focused task", e);
        }
        if (pkg == null || pkg.equals(mForeground)) return;
        mForeground = pkg;
        mHandler.removeCallbacks(mDynamicOn);
        if (isWritingApp(pkg)) {
            mAppMode = MODE_WRITING;
        } else if (GAME_APPS.contains(pkg)) {
            mAppMode = MODE_GAME;
        } else {
            mAppMode = MODE_NORMAL;
        }
        setMode(mAppMode, false);
        int writing = mAppMode == MODE_WRITING ? 1 : 0;
        ContentResolver cr = mContext.getContentResolver();
        if (Settings.System.getInt(cr, SETTING_WRITING, -1) != writing) {
            Settings.System.putInt(cr, SETTING_WRITING, writing);
        }
    }

    private void applyDynamic() {
        if (mAppMode == MODE_NORMAL) setMode(MODE_WRITING, false);
    }

    private void setMode(int mode, boolean force) {
        if (mode < 0 || (mode == mMode && !force)) return;
        if (LenovoHal.setBigPalm(mode)) {
            if (mode != mMode) Log.i(TAG, "palm mode " + mode + " (" + mForeground + ")");
            mMode = mode;
        }
    }

    private void updateRotation(boolean force) {
        Display display = mContext.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) return;
        int rotation = display.getRotation();
        if (rotation == mRotation && !force) return;
        if (LenovoHal.setEdgeInhibition(rotation)) {
            mRotation = rotation;
        }
    }
}
