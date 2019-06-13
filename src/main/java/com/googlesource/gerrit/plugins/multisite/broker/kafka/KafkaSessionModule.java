
package com.googlesource.gerrit.plugins.multisite.broker.kafka;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerSession;

public class KafkaSessionModule extends LifecycleModule {
	@Override
	  protected void configure() {
		 DynamicSet.bind(binder(),BrokerSession.class).to(KafkaSession.class);
	  }
}
