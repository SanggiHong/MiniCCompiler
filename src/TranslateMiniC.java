/**
 * Created by Hongssang on 2016-11-28.
 */
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class TranslateMiniC {
    public static void main(String[] args) throws Exception {
        MiniCLexer lexer = new MiniCLexer( new ANTLRFileStream("test.c"));
        CommonTokenStream tokens = new CommonTokenStream( lexer);
        MiniCParser parser = new MiniCParser( tokens );
        ParseTree tree = parser.program();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(new MiniCPrintListener(), tree);
    }
}