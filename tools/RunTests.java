import java.io.PrintWriter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

public final class RunTests {
    public static void main(String[] args) {
        String pkg = args.length > 0 ? args[0] : "dev.vfyjxf.cumulus.kernel";
        LauncherDiscoveryRequest req = request().selectors(selectPackage(pkg)).build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(req, listener);
        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out), 100);
        if (summary.getTotalFailureCount() > 0 || summary.getTestsFoundCount() == 0) {
            System.exit(1);
        }
    }
}
