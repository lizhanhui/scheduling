package functionaltests;

import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.executable.JavaExecutable;

import java.io.*;
import java.net.URI;


/**
 * A simple java executable used by {@link TestSmartProxy}
 * For all files in the localspace (non recursive into folders) creates an
 * output file with the same content and the .out extension.
 *
 * @author esalagea
 *
 */
public class SimpleJavaExecutable extends JavaExecutable {

    @Override
    public Serializable execute(TaskResult... results) throws Throwable {

        System.out.println("local space real uri: " + this.getLocalSpace().getRealURI());
        System.out.println("local space virtual uri: " + this.getLocalSpace().getVirtualURI());

        File localSpaceFolder = new File(URI.create(this.getLocalSpace().getRealURI()));
        System.out.println("Using localspace folder " + localSpaceFolder.getAbsolutePath());
        File[] files = localSpaceFolder.listFiles();

        for (File file : files) {

            if (file.isFile()) {
                System.out.println("Treating input file " + file.getAbsolutePath());

            } else {
                System.out.println(file.getAbsolutePath() + " is not a file. ");
            }
            String new_name = file.getName().replace("input", "output");
            new_name = new_name.replace(TestSmartProxy.inputFileExt, TestSmartProxy.outputFileExt);
            File fout = new File(file.getCanonicalFile().getParent(), new_name);
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            BufferedWriter bw = new BufferedWriter(new FileWriter(fout));

            String line;
            while ((line = br.readLine()) != null) {
                bw.write(line);
                bw.newLine();
            }
            bw.close();
            br.close();
            System.out.println("Written file " + fout.getAbsolutePath());
        }// for

        System.out.println("Task End");
        return "OK";
    }

}