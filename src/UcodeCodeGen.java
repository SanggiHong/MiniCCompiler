/**
 * Created by Hongssang on 2016-11-28.
 */
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class UcodeCodeGen {
    public static void main(String[] args) throws Exception {
        String mcFile;
        if(args.length == 0)
            mcFile = "test.c";
        else
            mcFile = args[0];
        MiniCLexer lexer = new MiniCLexer( new ANTLRFileStream(mcFile) );
        CommonTokenStream tokens = new CommonTokenStream( lexer );
        MiniCParser parser = new MiniCParser( tokens );
        ParseTree tree = parser.program();

        ParseTreeWalker walker = new ParseTreeWalker();
        UcodeGenListener generator = new UcodeGenListener();
        try {
            walker.walk(generator, tree);

            String fileName = "201102529.uco";
            try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(fileName))) {
                bw.write(generator.getUCode());
            }
            System.out.println("UCode was writed to " + fileName);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.print(generator.getExceptionDetail());
        }
    }
}