import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
/** PARSING ONLY. Does not resolve Minecraft types or prove Fabric compilation. */
public class SyntaxCheck {
    public static void main(String[] args)throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var fm=compiler.getStandardFileManager(diagnostics,null,null);var paths=Files.walk(Path.of(args[0]))) {
            var sources=paths.filter(p->p.toString().endsWith(".java")).map(Path::toFile).toList();
            var task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none","--release","21"),null,fm.getJavaFileObjectsFromFiles(sources));
            task.parse();
            for(var d:diagnostics.getDiagnostics())System.out.println(d);
            long errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
            System.out.println("SYNTAX_ONLY files="+sources.size()+" errors="+errors+"; Fabric type resolution NOT performed");
            if(errors>0)throw new AssertionError("Syntax errors");
        }
    }
}
