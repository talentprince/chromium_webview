// Copyright (c) 2013 weyoung. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.weyoung.chrome.impl;

import org.chromium.android_webview.JsPromptResultReceiver;
import org.weyoung.chrome.JsPromptResult;
import org.weyoung.chrome.JsResult;


/**
 * Proxies from android_webkit's JsResultReceiver to JsPromptResult.
 *
 * @hide
 */
public class ChromeJsPromptResultProxy implements JsResult.ResultReceiver {

    /** The proxy target. */
    private JsPromptResultReceiver mTarget;

    public ChromeJsPromptResultProxy(JsPromptResultReceiver target) {
      mTarget = target;
    }

    @Override
    public void onJsResultComplete(JsResult result) {
      JsPromptResult promptResult = (JsPromptResult)result;
      if (result.getResult()) {
        mTarget.confirm(promptResult.getStringResult());
      } else {
        mTarget.cancel();
      }
    }
}
