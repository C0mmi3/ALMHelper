package com.main.watchdog;

import com.main.helpers.ALMHelper;
import com.test.HelperBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;

/**
 * Created by ikfl27 on 13.04.2016.
 */
public class Observer {
    private static final Logger log = LogManager.getLogger(Observer.class.getName());

    /*
    Обновленный обзервер в рамках оптимизации.
    Цели оптимизации:
    1) Убрать иниициализацию избыточных объектов
    2) Убрать взаимные вызовы методов разных классов
    3) Упростить логику работы
     */
    public Observer(ALMHelper helper) throws IOException {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        Path path = Paths.get(helper.getConfig().getProperty("alm.defects.list.file.path"));
        path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        while (true) {
            WatchKey key;
            try {
                key = watcher.take();

            } catch (InterruptedException e) {
                log.error(e);
                return;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path fileName = ev.context();


                if (fileName.toString().equals("list.txt")) {
                    if (kind.name() == "ENTRY_CREATE" || kind.name() == "ENTRY_MODIFY") {
                        log.info(kind.name() + ": " + fileName + " .Starting processing!");
                        ALMHelper.process(helper);
                        log.info("We are still watching you!");
                    }
                }
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        }
    }
}