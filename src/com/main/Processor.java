package com.main;


import com.test.HelperBase;
import com.test.TestHelper;
import com.test.watchdog.Observer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;


/**
 * Created with IntelliJ IDEA.
 * User: ikfl27
 * Date: 17.03.16
 * Time: 18:22
 * To change this template use File | Settings | File Templates.
 */
public class Processor {
    private static final Logger log = LogManager.getLogger(Processor.class.getName());

    public static void main(String[] args) {
        String run = "";
        String archiveFlag = "";
        //ALMHelper helper = new ALMHelper();
        HelperBase helper = new TestHelper();
        if (args.length > 0) run = args[0];

        log.info("----------------------STARTING UP!----------------------");
        helper.getVersion();
        log.debug("CURRENT WORK MODE: " + run);
        if (run.equals("RUN_ONCE") || run.equals("")) {
            log.info("Module has been started in RUN_ONCE MODE!");
            log.info("Module doesn't support WATCH MODE currently.");
            //ALMHelper.process(helper);
            helper.process();
        } else if (run.equals("WATCH")) {
            log.info("Module has been started in WATCH MODE. It won't stop. I mean never. :)");
            try {
                Observer ob = new Observer(helper);
            } catch (IOException e) {
                log.error(e.toString());
            }
        } else
            log.error("There is no valid start-up argument.SHUTTING DOWN!");
    }
}
