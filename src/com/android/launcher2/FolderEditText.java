package com.android.launcher2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

/**
 * 文件夹名称编辑框
 */
public class FolderEditText extends EditText {

    private Folder mFolder;

    public FolderEditText(Context context) {
        super(context);
    }

    public FolderEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FolderEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
    }

    /* 
     * 在接收输入动作的任何方法之前执行，主要用于接收back事件以处理UI
     */
    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // Catch the back button on the soft keyboard so that we can just close the activity
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            mFolder.doneEditingFolderName(true);
        }
        return super.onKeyPreIme(keyCode, event);
    }
}
