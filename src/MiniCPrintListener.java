/**
 * Created by Hongssang on 2016-11-28.
 */
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.util.LinkedHashMap;
import java.util.Map;

public class MiniCPrintListener extends MiniCBaseListener {
    ParseTreeProperty<String> codeSet = new ParseTreeProperty<>();
    Map<String, Variable>[] varTable = new LinkedHashMap[2];
    StringBuilder[] additionalAssignment = new StringBuilder[2];
    static final int INDENT = 11;
    static final String INDENT_FORMAT = "%-"+INDENT+"s";
    static int maxGlobalOffset = 2;
    static int currentBase = 1;
    static int currentOffset = 2; // sym 1 1 은 쓰레기값 저장을 위한 공간(스택포인터를 조작하는 명령어가 없다)
    static int ifLabelNumber = 1;
    static int whileLabelNumber = 1;
    static boolean isReturnCalled = false;

    @Override
    public void enterProgram(MiniCParser.ProgramContext ctx) {
        super.enterProgram(ctx);
        additionalAssignment[0] = new StringBuilder();
        varTable[0] = new LinkedHashMap();
    }

    @Override
    public void exitProgram(MiniCParser.ProgramContext ctx) {
        super.exitProgram(ctx);
        System.out.print( String.format(INDENT_FORMAT + "sym 1 1 1\n", "") );
        for( Variable var : varTable[0].values() )
            System.out.print( String.format(INDENT_FORMAT + "sym %d %d %d\n", "", var.base, var.offset, var.size) );
        for ( MiniCParser.DeclContext declChild : ctx.decl() ) {
            System.out.print( codeSet.get(declChild) );
        }
        maxGlobalOffset--;
        System.out.print( String.format(INDENT_FORMAT + "bgn %d\n", "", maxGlobalOffset) );
        System.out.print( additionalAssignment[0].toString() );
        System.out.print( String.format(INDENT_FORMAT + "ldp\n", "") );
        System.out.print( String.format(INDENT_FORMAT + "call main\n", "") );
        System.out.print( String.format(INDENT_FORMAT + "end\n", "") );
    }

    @Override
    public void enterDecl(MiniCParser.DeclContext ctx) {
        super.enterDecl(ctx);
    }

    @Override
    public void exitDecl(MiniCParser.DeclContext ctx) {
        super.exitDecl(ctx);
        codeSet.put(ctx, codeSet.get(ctx.getChild(0)));
    }

    boolean isAssignmentIncluded(MiniCParser.Var_declContext ctx) { return ctx.getChildCount() == 5; }

    boolean isArrayDeclaration(MiniCParser.Var_declContext ctx) { return ctx.getChildCount() == 6; }

    @Override
    public void enterVar_decl(MiniCParser.Var_declContext ctx) {
        super.enterVar_decl(ctx);
        String varName = ctx.IDENT().getText();

        if (isAssignmentIncluded(ctx)) {
            String literal = ctx.LITERAL().getText();

            varTable[0].put(varName, new Variable(currentBase, currentOffset, 1));
            additionalAssignment[0].append( String.format(INDENT_FORMAT + "ldc %s\n", "", literal) )
                                        .append( String.format(INDENT_FORMAT + "str %d %d\n", "", currentBase, currentOffset) );
            currentOffset++;
        } else if (isArrayDeclaration(ctx)) {
            int size = Integer.parseInt(ctx.LITERAL().getText());
            varTable[0].put(varName, new Variable(currentBase, currentOffset, size));
            currentOffset += size;
        } else {
            varTable[0].put(varName, new Variable(currentBase, currentOffset, 1));
            currentOffset++;
        }
        maxGlobalOffset = currentOffset;

        codeSet.put(ctx, "");
    }

    @Override
    public void exitVar_decl(MiniCParser.Var_declContext ctx) {
        super.exitVar_decl(ctx);
    }

    @Override
    public void enterFun_decl(MiniCParser.Fun_declContext ctx) {
        super.enterFun_decl(ctx);
        additionalAssignment[1] = new StringBuilder();
        varTable[1] = new LinkedHashMap();
        currentOffset = 1;
        currentBase++;
        isReturnCalled = false;
    }

    @Override
    public void exitFun_decl(MiniCParser.Fun_declContext ctx) {
        super.exitFun_decl(ctx);
        StringBuilder uCode = new StringBuilder();
        String funcName = ctx.IDENT().getText(),
               statement = codeSet.get(ctx.compound_stmt());

        currentOffset--;
        uCode.append( String.format(INDENT_FORMAT + "proc %d 2 2\n", funcName, currentOffset) );
        for( Variable var : varTable[1].values() )
            uCode.append( String.format(INDENT_FORMAT + "sym %d %d %d\n", "", var.base, var.offset, var.size) );
        uCode.append( additionalAssignment[1].toString() )
                .append( statement );

        if (isReturnCalled)
            uCode.append( String.format(INDENT_FORMAT + "end\n", "") );
        else {
            uCode.append( String.format(INDENT_FORMAT + "ret\n", "") )
                    .append( String.format(INDENT_FORMAT + "end\n", "") );
        }

        currentBase--;
        codeSet.put(ctx, uCode.toString());
    }

    @Override
    public void enterParams(MiniCParser.ParamsContext ctx) {
        super.enterParams(ctx);
    }

    @Override
    public void exitParams(MiniCParser.ParamsContext ctx) {
        super.exitParams(ctx);
    }

    public boolean isArrayParam(MiniCParser.ParamContext ctx) { return ctx.getChildCount() == 4; }
    @Override
    public void enterParam(MiniCParser.ParamContext ctx) {
        super.enterParam(ctx);
        String paramName = ctx.IDENT().getText();

        if(isArrayParam(ctx))
            varTable[1].put(paramName, new Variable(currentBase, currentOffset, 0));
        else
            varTable[1].put(paramName, new Variable(currentBase, currentOffset, 1));

        currentOffset++;
    }

    @Override
    public void exitParam(MiniCParser.ParamContext ctx) {
        super.exitParam(ctx);
    }

    @Override
    public void enterStmt(MiniCParser.StmtContext ctx) {
        super.enterStmt(ctx);
    }

    @Override
    public void exitStmt(MiniCParser.StmtContext ctx) {
        super.exitStmt(ctx);
        codeSet.put(ctx, codeSet.get(ctx.getChild(0)));
    }

    @Override
    public void enterExpr_stmt(MiniCParser.Expr_stmtContext ctx) {
        super.enterExpr_stmt(ctx);
    }

    boolean isOnlyUnaryOperation(MiniCParser.Expr_stmtContext ctx) {
        return isUnaryOperation(ctx.expr())
                && ( UnaryOperation.INC.equals(ctx.expr().getChild(0).getText())
                    || UnaryOperation.DEC.equals(ctx.expr().getChild(0).getText()));
    }

    @Override
    public void exitExpr_stmt(MiniCParser.Expr_stmtContext ctx) {
        super.exitExpr_stmt(ctx);
        StringBuilder uCode = new StringBuilder();

        uCode.append( codeSet.get(ctx.expr()) );
        if (isOnlyUnaryOperation(ctx))
            uCode.append( String.format(INDENT_FORMAT + "str 1 1\n", "") );
        codeSet.put(ctx, uCode.toString());
    }

    @Override
    public void enterWhile_stmt(MiniCParser.While_stmtContext ctx) {
        super.enterWhile_stmt(ctx);
    }

    @Override
    public void exitWhile_stmt(MiniCParser.While_stmtContext ctx) {
        super.exitWhile_stmt(ctx);
        StringBuilder uCode = new StringBuilder();
        String condition = codeSet.get(ctx.expr()),
               statement = codeSet.get(ctx.stmt());

        uCode.append( String.format(INDENT_FORMAT + "nop\n", "WLEST" + whileLabelNumber) )
                .append( condition )
                .append( String.format(INDENT_FORMAT + "fjp %s\n", "", "WLEED" + whileLabelNumber) )
                .append( statement )
                .append( String.format(INDENT_FORMAT + "ujp %s\n", "", "WLEST" + whileLabelNumber) )
                .append( String.format(INDENT_FORMAT + "nop\n", "WLEED" + whileLabelNumber) );

        whileLabelNumber++;
        codeSet.put(ctx, uCode.toString());
    }

    @Override
    public void enterCompound_stmt(MiniCParser.Compound_stmtContext ctx) {
        super.enterCompound_stmt(ctx);
    }

    @Override
    public void exitCompound_stmt(MiniCParser.Compound_stmtContext ctx) {
        super.exitCompound_stmt(ctx);
        StringBuilder uCode = new StringBuilder();

        for ( MiniCParser.StmtContext stmtChild : ctx.stmt() ) {
            uCode.append( codeSet.get(stmtChild) );
        }

        codeSet.put(ctx, uCode.toString());
    }

    boolean isAssignmentIncluded(MiniCParser.Local_declContext ctx) { return ctx.getChildCount() == 5; }

    boolean isArrayDeclaration(MiniCParser.Local_declContext ctx) { return ctx.getChildCount() == 6; }

    @Override
    public void enterLocal_decl(MiniCParser.Local_declContext ctx) {
        super.enterLocal_decl(ctx);
        String varName = ctx.IDENT().getText();

        if (isAssignmentIncluded(ctx)) {
            String literal = ctx.LITERAL().getText();

            varTable[1].put(varName, new Variable(currentBase, currentOffset, 1));
            additionalAssignment[1].append( String.format(INDENT_FORMAT + "ldc %s\n", "", literal) )
                    .append( String.format(INDENT_FORMAT + "str %d %d\n", "", currentBase, currentOffset) );
            currentOffset++;
        } else if (isArrayDeclaration(ctx)) {
            int size = Integer.parseInt(ctx.LITERAL().getText());
            varTable[1].put(varName, new Variable(currentBase, currentOffset, size));
            currentOffset += size;
        } else {
            varTable[1].put(varName, new Variable(currentBase, currentOffset, 1));
            currentOffset++;
        }
    }

    @Override
    public void exitLocal_decl(MiniCParser.Local_declContext ctx) { super.exitLocal_decl(ctx); }

    boolean isElseIncluded(MiniCParser.If_stmtContext ctx) { return ctx.getChildCount() == 7; }

    @Override
    public void enterIf_stmt(MiniCParser.If_stmtContext ctx) {
        super.enterIf_stmt(ctx);
    }

    @Override
    public void exitIf_stmt(MiniCParser.If_stmtContext ctx) {
        super.exitIf_stmt(ctx);
        StringBuilder uCode = new StringBuilder();
        String condition = codeSet.get(ctx.expr());

        if (isElseIncluded(ctx)) {
            String ifStatement = codeSet.get(ctx.stmt(0)),
                   elseStatement = codeSet.get(ctx.stmt(1));
            uCode.append( condition )
                    .append( String.format(INDENT_FORMAT + "fjp %s\n", "", "ESST" + ifLabelNumber) )
                    .append( ifStatement )
                    .append( String.format(INDENT_FORMAT + "ujp %s\n", "", "ESED" + ifLabelNumber) )
                    .append( String.format(INDENT_FORMAT + "nop\n", "ESST" + ifLabelNumber) )
                    .append( elseStatement)
                    .append( String.format(INDENT_FORMAT + "nop\n", "ESED" + ifLabelNumber) );
        } else {
            String statement = codeSet.get(ctx.stmt(0));

            uCode.append( condition )
                    .append( String.format(INDENT_FORMAT + "fjp %s\n", "", "IFED" + ifLabelNumber) )
                    .append( statement )
                    .append( String.format(INDENT_FORMAT + "nop\n", "IFED" + ifLabelNumber) );
        }
        ifLabelNumber++;

        codeSet.put(ctx, uCode.toString());
    }

    @Override
    public void enterReturn_stmt(MiniCParser.Return_stmtContext ctx) {
        super.enterReturn_stmt(ctx);
    }

    public boolean isValueReturn(MiniCParser.Return_stmtContext ctx) {
        return ctx.getChildCount() == 3;
    }

    @Override
    public void exitReturn_stmt(MiniCParser.Return_stmtContext ctx) {
        super.exitReturn_stmt(ctx);
        StringBuilder uCode = new StringBuilder();
        isReturnCalled = true;

        if (isValueReturn(ctx)) {
            uCode.append(codeSet.get(ctx.expr()))
                    .append( String.format(INDENT_FORMAT + "retv\n", "") );
        } else {
            uCode.append( String.format(INDENT_FORMAT + "ret\n", "") );
        }

        codeSet.put(ctx, uCode.toString());
    }

    @Override
    public void enterExpr(MiniCParser.ExprContext ctx) {
        super.enterExpr(ctx);
    }

    boolean isBracketedOperand(MiniCParser.ExprContext ctx) {
        return ctx.IDENT() == null && ctx.getChildCount() == 3 && ctx.getChild(1) == ctx.expr(0);
    }

    boolean isIDENT(MiniCParser.ExprContext ctx) {
        return ctx.getChild(0) == ctx.IDENT();
    }

    boolean isLITERAL(MiniCParser.ExprContext ctx) {
        return ctx.getChild(0) == ctx.LITERAL();
    }

    boolean isOperand(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 1;
    }

    boolean isArrayOperand(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 4 && ctx.getChild(2) == ctx.expr(0);
    }

    boolean isFunctionCall(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 4 && ctx.getChild(2) == ctx.args();
    }

    boolean isUnaryOperation(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 2;
    }

    boolean isBinaryOperation(MiniCParser.ExprContext ctx) {
        return ctx.IDENT() == null && ctx.getChildCount() == 3 && ctx.getChild(1) != ctx.expr(0);
    }

    boolean isAssignment(MiniCParser.ExprContext ctx) {
        return ctx.IDENT() != null && ctx.getChildCount() == 3;
    }

    boolean isArrayAssignment(MiniCParser.ExprContext ctx) {
        return ctx.IDENT() != null && ctx.getChildCount() == 6;
    }

    @Override
    public void exitExpr(MiniCParser.ExprContext ctx) {
        super.exitExpr(ctx);
        StringBuilder uCode = new StringBuilder();

        if (isBracketedOperand(ctx)) {
            uCode.append( codeSet.get(ctx.expr(0)) );
        }

        else if (isOperand(ctx)) {
            if (isIDENT(ctx)) {
                Variable operand = varTable[1].get( ctx.getChild(0).getText() );
                if (operand == null)
                    operand = varTable[0].get( ctx.getChild(0).getText() );

                if (operand.isNeedToLoadAddr())
                    uCode.append( String.format(INDENT_FORMAT + "lda %d %d\n", "", operand.base, operand.offset) );
                else
                    uCode.append( String.format(INDENT_FORMAT + "lod %d %d\n", "", operand.base, operand.offset) );
            } else if (isLITERAL(ctx)) {
                String operand = ctx.getChild(0).getText();
                uCode.append( String.format(INDENT_FORMAT + "ldc %s\n", "", operand) );
            }
        }

        else if (isArrayOperand(ctx)) {
            Variable arrayId = varTable[1].get( ctx.IDENT().getText() );
            if (arrayId == null)
                arrayId = varTable[0].get( ctx.getChild(0).getText() );

            String arrayNumber = codeSet.get( ctx.expr(0) );

            if (arrayId.isNeedToLoadAddr()) {
                uCode.append(arrayNumber)
                        .append(String.format(INDENT_FORMAT + "lda %d %d\n", "", arrayId.base, arrayId.offset))
                        .append(String.format(INDENT_FORMAT + "add\n", ""))
                        .append(String.format(INDENT_FORMAT + "ldi\n", ""));
            } else {
                uCode.append(arrayNumber)
                        .append(String.format(INDENT_FORMAT + "lod %d %d\n", "", arrayId.base, arrayId.offset))
                        .append(String.format(INDENT_FORMAT + "add\n", ""))
                        .append(String.format(INDENT_FORMAT + "ldi\n", ""));
            }
        }

        else if (isFunctionCall(ctx)) {
            String funcName = ctx.IDENT().getText(),
                    args = codeSet.get(ctx.args());
            uCode.append( String.format(INDENT_FORMAT + "ldp\n", "") )
                    .append( args )
                    .append( String.format(INDENT_FORMAT + "call %s\n", "", funcName) );
        }

        else if (isUnaryOperation(ctx)) {
            String op = ctx.getChild(0).getText();
            String operand = codeSet.get(ctx.expr(0));

            uCode.append(operand);
            for ( UnaryOperation u_op : UnaryOperation.values() ) {
                if (u_op.equals(op)) { //일항 연산자의 경우, ++, --의 경우는 출현과 동시에 실제 변수에 영향을 미침
                    uCode.append( String.format(INDENT_FORMAT + u_op, "") );
                    if (u_op.isNeededAssignment()) { //++, -- 일 경우, assign 후 스택에 다시 push
                        if (ctx.expr(0).IDENT() == null) {
                            System.out.println("error");
                            break;
                        }
                        Variable id = varTable[1].get(ctx.expr(0).IDENT().getText());
                        if (id == null)
                            id = varTable[0].get( ctx.getChild(0).getText() );
                        if (id.isArrayVariable()) { //array 인자의 ++, -- 연산
                            String arrayNumber = codeSet.get(ctx.expr(0).expr(0));
                            if(id.isNeedToLoadAddr()) { //지역변수 array\ 인자
                                uCode.append( String.format(INDENT_FORMAT + "dup\n", "") )
                                        .append( arrayNumber )
                                        .append( String.format(INDENT_FORMAT + "lda %d %d\n", "", id.base, id.offset) )
                                        .append( String.format(INDENT_FORMAT + "add\n", "") )
                                        .append( String.format(INDENT_FORMAT + "swp\n", "") )
                                        .append( String.format(INDENT_FORMAT + "sti\n", "") );
                            } else { //매개변수 array 인자
                                uCode.append( String.format(INDENT_FORMAT + "dup\n", "") )
                                        .append( arrayNumber )
                                        .append( String.format(INDENT_FORMAT + "lod %d %d\n", "", id.base, id.offset) )
                                        .append( String.format(INDENT_FORMAT + "add\n", "") )
                                        .append( String.format(INDENT_FORMAT + "swp\n", ""))
                                        .append( String.format(INDENT_FORMAT + "sti\n", "") );
                            }
                        } else { //일반 변수의 ++, -- 연산
                            uCode.append( String.format(INDENT_FORMAT + "dup\n", "") )
                                    .append( String.format(INDENT_FORMAT + "str %d %d\n", "", id.base, id.offset) );
                        }
                    }
                    break;
                }
            }
        }

        else if (isBinaryOperation(ctx)) {
            String op = ctx.getChild(1).getText();
            String operand_1 = codeSet.get(ctx.expr(0)),
                   operand_2 = codeSet.get(ctx.expr(1));

            for ( BinaryOperation b_op : BinaryOperation.values() ) {
                if (b_op.equals(op)) {
                    uCode.append(operand_1)
                            .append(operand_2)
                            .append( String.format(INDENT_FORMAT + b_op, "") );
                    break;
                }
            }
        }

        else if (isAssignment(ctx)) {
            Variable id = varTable[1].get( ctx.IDENT().getText() );
            if (id == null)
                id = varTable[0].get( ctx.getChild(0).getText() );
            String expr = codeSet.get(ctx.expr(0));
            uCode.append( expr )
                    .append( String.format(INDENT_FORMAT + "str %d %d\n", "", id.base, id.offset) );
        }

        else if (isArrayAssignment(ctx)) {
            Variable arrayId = varTable[1].get( ctx.IDENT().getText() );
            if (arrayId == null)
                arrayId = varTable[0].get( ctx.getChild(0).getText() );
            String arrayNumber = codeSet.get(ctx.expr(0)),
                    expr = codeSet.get(ctx.expr(1));

            if(arrayId.isNeedToLoadAddr()) {
                uCode.append(arrayNumber)
                        .append( String.format(INDENT_FORMAT + "lda %d %d\n", "", arrayId.base, arrayId.offset) )
                        .append( String.format(INDENT_FORMAT + "add\n", "") )
                        .append( expr )
                        .append( String.format(INDENT_FORMAT + "sti\n", "") );
            } else {
                uCode.append(arrayNumber)
                        .append( String.format(INDENT_FORMAT + "lod %d %d\n", "", arrayId.base, arrayId.offset) )
                        .append( String.format(INDENT_FORMAT + "add\n", "") )
                        .append( expr )
                        .append( String.format(INDENT_FORMAT + "sti\n", "") );
            }
        }

        codeSet.put(ctx, uCode.toString());
    }

    @Override
    public void enterArgs(MiniCParser.ArgsContext ctx) {
        super.enterArgs(ctx);
    }

    @Override
    public void exitArgs(MiniCParser.ArgsContext ctx) {
        super.exitArgs(ctx);
        StringBuilder uCode = new StringBuilder();

        for ( MiniCParser.ExprContext exprChild : ctx.expr() ) {
            uCode.append( codeSet.get(exprChild) );
        }

        codeSet.put(ctx, uCode.toString());
    }

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
        super.enterEveryRule(ctx);
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
        super.exitEveryRule(ctx);
    }

    @Override
    public void visitTerminal(TerminalNode node) {
        super.visitTerminal(node);
    }

    @Override
    public void visitErrorNode(ErrorNode node) {
        super.visitErrorNode(node);
    }

    class Variable {
        int base, offset, size;
        public Variable(int base, int offset, int size) {
            this.base = base;
            this.offset = offset;
            this.size = size;
        }

        public boolean isArrayVariable() {
            return size != 1;
        }

        public boolean isNeedToLoadAddr() {
            return size > 1;
        }
    }

    enum BinaryOperation {
        MUL("*") { public String toString() { return "mult" + "\n"; } },
        DIV("/") { public String toString() { return "div" + "\n"; } },
        MOD("%") { public String toString() { return "mod" + "\n"; } },
        PLUS("+") { public String toString() { return "add" + "\n"; } },
        MINUS("-") { public String toString() { return "sub" + "\n"; } },
        EQ("==") { public String toString() { return "eq" + "\n"; } },
        NE("!=") { public String toString() { return "ne" + "\n"; } },
        LE("<=") { public String toString() { return "le" + "\n"; } },
        GE(">=") { public String toString() { return "ge" + "\n"; } },
        GT(">") { public String toString() { return "gt" + "\n"; } },
        LT("<") { public String toString() { return "lt" + "\n"; } },
        AND("and") { public String toString() { return "and" + "\n"; } },
        OR("or") { public String toString() { return "or" + "\n"; } };

        private final String symbol;
        BinaryOperation(String symbol) { this.symbol = symbol; }
        public boolean equals(String symbol) { return this.symbol.equals(symbol); }
        @Override abstract public String toString();
    }

    enum UnaryOperation {
        INC("++") { public String toString() { return "inc" + "\n"; } },
        DEC("--") { public String toString() { return "dec" + "\n"; } },
        NEG("-") { public String toString() { return "neg" + "\n"; } },
        NOT("!") { public String toString() { return "notop" + "\n"; } };

        private final String symbol;
        UnaryOperation(String symbol) { this.symbol = symbol; }
        public boolean equals(String symbol) { return this.symbol.equals(symbol); }
        public boolean isNeededAssignment() { return this.equals("++") || this.equals("--"); }
        @Override abstract public String toString();
    }
}