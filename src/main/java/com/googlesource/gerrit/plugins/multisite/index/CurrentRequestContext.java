// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.multisite.index;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Consumer;

@Singleton
public class CurrentRequestContext {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private ThreadLocalRequestContext threadLocalCtx;
  private OneOffRequestContext oneOffCtx;

  @Inject
  public CurrentRequestContext(
      ThreadLocalRequestContext threadLocalCtx, OneOffRequestContext oneOffCtx) {
    this.threadLocalCtx = threadLocalCtx;
    this.oneOffCtx = oneOffCtx;
  }

  public void onlyWithContext(Consumer<RequestContext> body) {
    RequestContext ctx = threadLocalCtx.getContext();
    if (ctx == null) {
      logger.atFine().log("No context, skipping event (index.synchronizeForced is false)");
      return;
    }

    if (ctx == null) { // TODO this is valis if there is feature toggle and is true
      try (ManualRequestContext manualCtx = oneOffCtx.open()) {
        body.accept(manualCtx);
      }
    } else {
      body.accept(ctx);
    }
  }
}
