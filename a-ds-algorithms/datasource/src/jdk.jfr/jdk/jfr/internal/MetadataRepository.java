/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package jdk.jfr.internal;

import static jdk.jfr.internal.LogLevel.DEBUG;
import static jdk.jfr.internal.LogTag.JFR_SYSTEM;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.RequestEngine.RequestHook;
import jdk.jfr.internal.consumer.RepositoryFiles;
import jdk.jfr.internal.handlers.EventHandler;

public final class MetadataRepository {

    private static final JVM jvm = JVM.getJVM();
    private static final MetadataRepository instance = new MetadataRepository();

    private final List<EventType> nativeEventTypes = new ArrayList<>(100);
    private final List<EventControl> nativeControls = new ArrayList<EventControl>(100);
    private final TypeLibrary typeLibrary = TypeLibrary.getInstance();
    private final SettingsManager settingsManager = new SettingsManager();
    private final Map<String, Class<? extends Event>> mirrors = new HashMap<>();
    private boolean staleMetadata = true;
    private boolean unregistered;
    private long lastUnloaded = -1;
    private Instant outputChange;

    public MetadataRepository() {
        initializeJVMEventTypes();
    }

    private void initializeJVMEventTypes() {
        List<RequestHook> requestHooks = new ArrayList<>();
        for (Type type : typeLibrary.getTypes()) {
            if (type instanceof PlatformEventType pEventType) {
                EventType eventType = PrivateAccess.getInstance().newEventType(pEventType);
                pEventType.setHasDuration(eventType.getAnnotation(Threshold.class) != null);
                pEventType.setHasStackTrace(eventType.getAnnotation(StackTrace.class) != null);
                pEventType.setHasCutoff(eventType.getAnnotation(Cutoff.class) != null);
                pEventType.setHasThrottle(eventType.getAnnotation(Throttle.class) != null);
                pEventType.setHasPeriod(eventType.getAnnotation(Period.class) != null);
                // Must add hook before EventControl is created as it removes
                // annotations, such as Period and Threshold.
                if (pEventType.hasPeriod()) {
                    pEventType.setEventHook(true);
                    if (!pEventType.isMethodSampling()) {
                        requestHooks.add(new RequestHook(pEventType));
                    }
                }
                nativeControls.add(new EventControl(pEventType));
                nativeEventTypes.add(eventType);
            }
        }
        RequestEngine.addHooks(requestHooks);
    }

    public static MetadataRepository getInstance() {
        return instance;
    }

    public synchronized List<EventType> getRegisteredEventTypes() {
        List<EventHandler> handlers = getEventHandlers();
        List<EventType> eventTypes = new ArrayList<>(handlers.size() + nativeEventTypes.size());
        for (EventHandler h : handlers) {
            if (h.isRegistered()) {
                eventTypes.add(h.getEventType());
            }
        }
        eventTypes.addAll(nativeEventTypes);
        return eventTypes;
    }

    public synchronized EventType getEventType(Class<? extends jdk.internal.event.Event> eventClass) {
        EventHandler h = getHandler(eventClass, false);
        if (h != null && h.isRegistered()) {
            return h.getEventType();
        }
        throw new IllegalStateException("Event class " + eventClass.getName() + " is not registered");
    }

    public synchronized void unregister(Class<? extends Event> eventClass) {
        Utils.checkRegisterPermission();
        EventHandler handler = getHandler(eventClass, false);
        if (handler != null) {
            handler.setRegistered(false);
        }
        // never registered, ignore call
    }
    public synchronized EventType register(Class<? extends jdk.internal.event.Event> eventClass) {
        return register(eventClass, Collections.emptyList(), Collections.emptyList());
    }

    public synchronized EventType register(Class<? extends jdk.internal.event.Event> eventClass, List<AnnotationElement> dynamicAnnotations, List<ValueDescriptor> dynamicFields) {
        Utils.checkRegisterPermission();
        EventHandler handler = getHandler(eventClass, true);
        if (handler == null) {
            if (eventClass.getAnnotation(MirrorEvent.class) != null) {
                // don't register mirrors
                return null;
            }
            PlatformEventType pe = findMirrorType(eventClass);
            handler = makeHandler(eventClass, pe, dynamicAnnotations, dynamicFields);
        }
        handler.setRegistered(true);
        typeLibrary.addType(handler.getPlatformEventType());
        if (jvm.isRecording()) {
            settingsManager.setEventControl(handler.getEventControl(), true);
            settingsManager.updateRetransform(Collections.singletonList((eventClass)));
       }
       setStaleMetadata();
       return handler.getEventType();
    }

    private PlatformEventType findMirrorType(Class<? extends jdk.internal.event.Event> eventClass) throws InternalError {
        String fullName = eventClass.getModule().getName() + ":" + eventClass.getName();
        Class<? extends Event> mirrorClass = mirrors.get(fullName);
        if (mirrorClass == null) {
            return null; // not a mirror
        }
        Utils.verifyMirror(mirrorClass, eventClass);
        PlatformEventType et = (PlatformEventType) TypeLibrary.createType(mirrorClass);
        typeLibrary.removeType(et.getId());
        long id = Type.getTypeId(eventClass);
        et.setId(id);
        return et;
    }

    private EventHandler getHandler(Class<? extends jdk.internal.event.Event> eventClass, boolean ensureInitialized) {
        Utils.ensureValidEventSubclass(eventClass);
        SecuritySupport.makeVisibleToJFR(eventClass);
        if (ensureInitialized) {
            Utils.ensureInitialized(eventClass);
        }
        return Utils.getHandler(eventClass);
    }

    private EventHandler makeHandler(Class<? extends jdk.internal.event.Event> eventClass, PlatformEventType pEventType, List<AnnotationElement> dynamicAnnotations, List<ValueDescriptor> dynamicFields) throws InternalError {
        SecuritySupport.addHandlerExport(eventClass);
        if (pEventType == null) {
            pEventType = (PlatformEventType) TypeLibrary.createType(eventClass, dynamicAnnotations, dynamicFields);
        }
        EventType eventType = PrivateAccess.getInstance().newEventType(pEventType);
        EventControl ec = new EventControl(pEventType, eventClass);
        Class<? extends EventHandler> handlerClass = null;
        try {
            String eventHandlerName = EventHandlerCreator.makeEventHandlerName(eventType.getId());
            handlerClass = Class.forName(eventHandlerName, false, Event.class.getClassLoader()).asSubclass(EventHandler.class);
            // Created eagerly on class load, tag as instrumented
            pEventType.setInstrumented();
            Logger.log(JFR_SYSTEM, DEBUG, "Found existing event handler for " + eventType.getName());
       } catch (ClassNotFoundException cne) {
           EventHandlerCreator ehc = new EventHandlerCreator(eventType.getId(),  ec.getSettingInfos(), eventType, eventClass);
           handlerClass = ehc.makeEventHandlerClass();
           Logger.log(LogTag.JFR_SYSTEM, DEBUG, "Created event handler for " + eventType.getName());
       }
        EventHandler handler = EventHandlerCreator.instantiateEventHandler(handlerClass, true, eventType, ec);
        Utils.setHandler(eventClass, handler);
        return handler;
    }

    public synchronized void setSettings(List<Map<String, String>> list, boolean writeSettingEvents) {
        settingsManager.setSettings(list, writeSettingEvents);
    }

    synchronized void disableEvents() {
        for (EventControl c : getEventControls()) {
            c.disable();
        }
    }

    public synchronized List<EventControl> getEventControls() {
        List<Class<? extends jdk.internal.event.Event>> eventClasses = jvm.getAllEventClasses();
        ArrayList<EventControl> controls = new ArrayList<>(eventClasses.size() + nativeControls.size());
        controls.addAll(nativeControls);
        for (Class<? extends jdk.internal.event.Event> clazz : eventClasses) {
            EventHandler eh = Utils.getHandler(clazz);
            if (eh != null) {
                controls.add(eh.getEventControl());
            }
        }
        return controls;
    }

    private void storeDescriptorInJVM() throws InternalError {
        jvm.storeMetadataDescriptor(getBinaryRepresentation());
        staleMetadata = false;
    }

    private static List<EventHandler> getEventHandlers() {
        List<Class<? extends jdk.internal.event.Event>> allEventClasses = jvm.getAllEventClasses();
        List<EventHandler> eventHandlers = new ArrayList<>(allEventClasses.size());
        for (Class<? extends jdk.internal.event.Event> clazz : allEventClasses) {
            EventHandler eh = Utils.getHandler(clazz);
            if (eh != null) {
                eventHandlers.add(eh);
            }
        }
        return eventHandlers;
    }

    private byte[] getBinaryRepresentation() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(40000);
        DataOutputStream daos = new DataOutputStream(baos);
        try {
            List<Type> types = typeLibrary.getTypes();
            Collections.sort(types);
            MetadataDescriptor.write(types, daos);
            daos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            // should not happen
            throw new InternalError(e);
        }
    }

    synchronized boolean isEnabled(String eventName) {
        return settingsManager.isEnabled(eventName);
    }

    synchronized void setStaleMetadata() {
        staleMetadata = true;
    }

    // Lock around setOutput ensures that other threads don't
    // emit events after setOutput and unregister the event class, before a call
    // to storeDescriptorInJVM
    synchronized Instant setOutput(String filename) {
        if (staleMetadata) {
            storeDescriptorInJVM();
        }
        jvm.setOutput(filename);
        // Each chunk needs a unique start timestamp and
        // if the clock resolution is low, two chunks may
        // get the same timestamp. Utils.getChunkStartNanos()
        // ensures the timestamp is unique for the next chunk
        long chunkStart = Utils.getChunkStartNanos();
        if (filename != null) {
            RepositoryFiles.notifyNewFile();
        }
        unregisterUnloaded();
        if (unregistered) {
            if (typeLibrary.clearUnregistered()) {
                storeDescriptorInJVM();
            }
            unregistered = false;
        }
        return Utils.epochNanosToInstant(chunkStart);
    }

    private void unregisterUnloaded() {
        long unloaded = jvm.getUnloadedEventClassCount();
        if (this.lastUnloaded != unloaded) {
            this.lastUnloaded = unloaded;
            List<Class<? extends jdk.internal.event.Event>> eventClasses = jvm.getAllEventClasses();
            HashSet<Long> knownIds = new HashSet<>(eventClasses.size());
            for (Class<? extends jdk.internal.event.Event>  ec: eventClasses) {
                knownIds.add(Type.getTypeId(ec));
            }
            for (Type type : typeLibrary.getTypes()) {
                if (type instanceof PlatformEventType pe) {
                    if (!knownIds.contains(pe.getId())) {
                        if (!pe.isJVM()) {
                            pe.setRegistered(false);
                        }
                    }
                }
            }
        }
    }

    synchronized void setUnregistered() {
       unregistered = true;
    }

    public synchronized void registerMirror(Class<? extends Event> eventClass) {
        MirrorEvent me = eventClass.getAnnotation(MirrorEvent.class);
        if (me != null) {
            String fullName = me.module() + ":" + me.className();
            mirrors.put(fullName, eventClass);
            return;
        }
        throw new InternalError("Mirror class must have annotation " + MirrorEvent.class.getName());
    }

    public synchronized void flush() {
        if (staleMetadata) {
            storeDescriptorInJVM();
        }
        jvm.flush();
    }

}
