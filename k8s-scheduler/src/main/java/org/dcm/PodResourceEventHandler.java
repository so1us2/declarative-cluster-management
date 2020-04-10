/*
 * Copyright © 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier: BSD-2
 */

package org.dcm;

import io.fabric8.kubernetes.api.model.Pod;
import io.reactivex.processors.PublishProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Subscribes to Kubernetes pod events and streams them to a flowable. Notably, it does not write
 * it to the database unlike the NodeResourceEventHandler. We do this to have tigher control over
 * batching these writes to the database.
 */
class PodResourceEventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PodResourceEventHandler.class);
    private final PublishProcessor<PodEvent> flowable;
    private final ExecutorService service;

    PodResourceEventHandler(final PublishProcessor<PodEvent> flowable) {
        this.flowable = flowable;
        this.service = Executors.newFixedThreadPool(10);
    }

    PodResourceEventHandler(final PublishProcessor<PodEvent> flowable, final ExecutorService service) {
        this.flowable = flowable;
        this.service = service;
    }


    public void onAddSync(final Pod pod) {
        LOG.trace("{} pod add received", pod.getMetadata().getName());
        flowable.onNext(new PodEvent(PodEvent.Action.ADDED, pod)); // might be better to add pods in a batch
    }

    public void onUpdateSync(final Pod newPod) {
        LOG.trace("{} pod update received", newPod.getMetadata().getName());
        flowable.onNext(new PodEvent(PodEvent.Action.UPDATED, newPod));
    }

    public void onDeleteSync(final Pod pod) {
        final long now = System.nanoTime();
        LOG.trace("{} pod deleted in {}ns!", pod.getMetadata().getName(), (System.nanoTime() - now));
        flowable.onNext(new PodEvent(PodEvent.Action.DELETED, pod));
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