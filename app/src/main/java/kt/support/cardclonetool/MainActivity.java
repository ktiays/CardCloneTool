package kt.support.cardclonetool;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Objects;

enum Status {
    INIT, BLOCK0_CALCULATED, CLONED
}

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;

    private String id;
    private String studentID;

    private AlphaAnimation fadeIn = new AlphaAnimation(0, 1);
    private AlphaAnimation fadeOut = new AlphaAnimation(1, 0);

    private TextView tip;
    private ViewGroup viewGroup;
    private Button write;
    private TextView uid;
    private TextView sid;
    private TextView another;

    private Status status = Status.INIT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 设置状态栏及窗口属性
        Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tip = findViewById(R.id.tip);
        write = findViewById(R.id.write);
        sid = findViewById(R.id.id);
        uid = findViewById(R.id.uid);
        another = findViewById(R.id.other_card);
        viewGroup = findViewById(R.id.input_group);

        viewGroup.setVisibility(View.INVISIBLE);
        another.setVisibility(View.INVISIBLE);
        fadeIn.setDuration(600);
        fadeIn.setFillAfter(true);
        fadeOut.setDuration(600);
        fadeOut.setFillAfter(true);
        write.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                status = Status.BLOCK0_CALCULATED;
                write.clearAnimation();
                write.setAnimation(fadeOut);
                another.clearAnimation();
                another.setVisibility(View.VISIBLE);
                another.setAnimation(fadeIn);
            }
        });

        // 自定义显示 ActionBar
        ActionBar actionBar = getSupportActionBar();
        Objects.requireNonNull(actionBar).setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(R.layout.actionbar_layout);
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    @Override
    protected void onStart() {
        super.onStart();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            // 设备不支持 NFC 功能
            new KTBuild(this)
                    .setTitle(getString(R.string.nfc_unavailable_title))
                    .setMessage(getString(R.string.nfc_unavailable_message))
                    .setPositiveButton(getString(R.string.exit), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
        } else if (!nfcAdapter.isEnabled()) {
            // NFC 功能未开启
            new KTBuild(this)
                    .setTitle(getString(R.string.nfc_unopened_title))
                    .setMessage(getString(R.string.nfc_unopened_message))
                    .setPositiveButton(getString(R.string.exit), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 优先处理 NFC
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }  // 不可用

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 防止被再次唤醒时意外退出
        if (!(intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) instanceof Tag)) {
            return;
        }
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        try {
            final MTool mTool = new MTool(tag);
            if (mTool.isNull()) {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show();
                return;
            }

            // 读取0块数据并暂存
            switch (status) {
                case INIT:
                    studentID = mTool.getStudentID();
                    if (studentID == null) {
                        Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        id = mTool.getBlock0();
                        sid.setText(studentID);
                        uid.setText(mTool.getBlock0().substring(0, 8));
                        tip.setAnimation(fadeOut);
                        viewGroup.setVisibility(View.VISIBLE);
                        viewGroup.setAnimation(fadeIn);
                    }
                    break;
                case BLOCK0_CALCULATED:
                    final StringBuilder stringBuilder = new StringBuilder(id);
                    stringBuilder.replace(10, 16, mTool.getBlock0().substring(10, 16));
                    id = stringBuilder.toString();

                    new KTBuild(this)
                            .setTitle(R.string.tag_checked)
                            .setMessage(R.string.write_alert)
                            .setPositiveButton(R.string.write_confirm, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // 写入UID
                                    try {
                                        mTool.writeBlock(0, 0, id);
                                        status = Status.CLONED;
                                        mTool.close();
                                    } catch (IOException ignored) {
                                        Toast.makeText(MainActivity.this, R.string.exception, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .setCancelable(false)
                            .show();
                    break;
                case CLONED:
                    if (!mTool.getBlock0().equals(id))
                        Toast.makeText(this, R.string.uid_error, Toast.LENGTH_SHORT).show();
                    else {
                        // 写入学号信息
                        mTool.writeBlock(15, 0, studentID);
                        viewGroup.clearAnimation();
                        viewGroup.setAnimation(fadeOut);
                        tip.clearAnimation();
                        tip.setAnimation(fadeIn);
                        write.clearAnimation();
                        another.clearAnimation();
                        another.setVisibility(View.INVISIBLE);
                        id = null;
                        studentID = null;
                        status = Status.INIT;
                        Toast.makeText(this, R.string.success, Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        } catch (IOException ignored) {
            Toast.makeText(this, getString(R.string.exception), Toast.LENGTH_SHORT).show();
        }
    }

    // 圆角对话框构造器
    static class KTBuild extends AlertDialog.Builder {

        KTBuild(Context context) {
            super(context);
            setCancelable(false);
        }

        @Override
        public AlertDialog create() {
            AlertDialog alertDialog = super.create();
            Objects.requireNonNull(alertDialog.getWindow()).setBackgroundDrawableResource(R.drawable.ktdialog_view);
            return alertDialog;
        }
    }
}
