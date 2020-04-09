/*
 * Copyright © 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier: BSD-2
 */

package org.dcm;

import io.fabric8.kubernetes.api.model.Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Subscribes to Kubernetes pod events and streams them to a flowable. Notably, it does not write
 * it to the database unlike the NodeResourceEventHandler. We do this to have tigher control over
 * batching these writes to the database.
 */
class PodResourceEventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PodResourceEventHandler.class);
    private final BlockingQueue<PodEvent> flowable;
    private final ExecutorService service;
    private final PodEventsToDatabase podEventsToDatabase;

    PodResourceEventHandler(final BlockingQueue<PodEvent> flowable,
                            final PodEventsToDatabase podEventsToDatabase) {
        this.flowable = flowable;
        this.podEventsToDatabase = podEventsToDatabase;
        this.service = Executors.newFixedThreadPool(10);
    }

    PodResourceEventHandler(final BlockingQueue<PodEvent> flowable, final PodEventsToDatabase podEventsToDatabase,
                            final ExecutorService service) {
        this.flowable = flowable;
        this.podEventsToDatabase = podEventsToDatabase;
        this.service = service;
    }

    public void onAddSync(final Pod pod) {
        LOG.debug("{} pod add received", pod.getMetadata().getName());
        final PodEvent event = new PodEvent(PodEvent.Action.ADDED, pod);
        podEventsToDatabase.handle(event);
        flowable.add(event); // might be better to add pods in a batch
    }

    public void onUpdateSync(final Pod newPod) {
        LOG.debug("{} pod update received", newPod.getMetadata().getName());
        final PodEvent event = new PodEvent(PodEvent.Action.UPDATED, newPod);
        podEventsToDatabase.handle(event);
        flowable.add(event);
    }

    public void onDeleteSync(final Pod pod) {
        final long now = System.nanoTime();
        LOG.debug("{} pod deleted in {}ns!", pod.getMetadata().getName(), (System.nanoTime() - now));
        final PodEvent event = new PodEvent(PodEvent.Action.DELETED, pod);
        podEventsToDatabase.handle(event);
        flowable.add(event);
    }

    public void onAdd(final Pod pod) {
        service.execute(() -> onAddSync(pod));
    }

    public void onUpdate(final Pod newPod) {
        service.execute(() -> onUpdateSync(newPod));
    }

    public void onDelete(final Pod pod) {
        service.execute(() -> onDeleteSync(pod));
    }
}