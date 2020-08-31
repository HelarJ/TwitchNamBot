package ChatBot;

import java.io.IOException;
import java.util.logging.Handler;

public class ConsoleMain {
    private static MainThread mainThread;
    public static void main(String[] args) throws IOException, InterruptedException {
        Running.start();
        while (Running.getRunning()){
            if (args.length == 0){
                args = new String[] {"#moonmoon"};
            }
            mainThread = new MainThread(args);
            Thread mt = new Thread(mainThread);
            try {
                mt.start();
                mt.join();
            } catch (InterruptedException e) {
                mainThread.closeThreads();
                Running.getLogger().severe("Exception in main thread.");
                Running.getLogger().info(e.getMessage());
            } finally {
                mt.join();
                if (Running.getRunning()){
                    Running.getLogger().info("Attempting to reconnect...");
                }
            }
        }
        for (Handler handler : Running.getLogger().getHandlers()){
            handler.close();
        }
    }

    public static void reconnect(){
        mainThread.closeThreads();
    }
}
