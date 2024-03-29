/**
 * Created by Hongssang on 2016-11-28.
 */
import com.sun.xml.internal.bind.annotation.OverrideAnnotationOf;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;


public class UcodeGenListener extends MiniCBaseListener {
    private ParseTreeProperty<String> codeSet = new ParseTreeProperty<>();
    private Map<String, Variable>[] varTable = new LinkedHashMap[2];
    private Stack<Type> partialAST = new Stack<>();
    private Map<String, Function> funcTable = new LinkedHashMap<>();
    private Stack<Function> currentFunction = new Stack<>();
    private static boolean isReturnCalled = false;
    private static final int INDENT = 11;
    private static final String INDENT_FORMAT = "%-"+INDENT+"s";
    private static int maxGlobalOffset = 2;
    private static int currentBase = 1;
    private static int currentOffset = 2; // sym 1 1 은 쓰레기값 저장을 위한 공간(스택포인터를 조작하는 명령어가 없다)
    private static int ifLabelNumber = 1;
    private static int whileLabelNumber = 1;
    private StringBuilder[] additionalAssignment = new StringBuilder[2];
    private String completeUCode;
    private StringBuilder exception = new StringBuilder();

    protected String getUCode() {
        return completeUCode;
    }

    protected String getExceptionDetail() {
        return exception.toString();
    }

    @Override
    public void enterProgram(MiniCParser.ProgramContext ctx) {
        super.enterProgram(ctx);
        additionalAssignment[0] = new StringBuilder();
        varTable[0] = new LinkedHashMap<>();

        Function temp = new Function("void", "write");
        temp.addParam(Type.INT);
        funcTable.put("write", temp);
    }

    @Override
    public void exitProgram(MiniCParser.ProgramContext ctx) {
        super.exitProgram(ctx);
        StringBuilder uCode = new StringBuilder();
        uCode.append( String.format(INDENT_FORMAT + "sym 1 1 1\n", "") );
        for( Variable var : varTable[0].values() )
            uCode.append( String.format(INDENT_FORMAT + "sym %d %d %d\n", "", var.base, var.offset, var.size) );
        for ( MiniCParser.DeclContext declChild : ctx.decl() ) {
            uCode.append( codeSet.get(declChild) );
        }
        maxGlobalOffset--;
        uCode.append( String.format(INDENT_FORMAT + "bgn %d\n", "", maxGlobalOffset) );
        uCode.append( additionalAssignment[0].toString() );
        uCode.append( String.format(INDENT_FORMAT + "ldp\n", "") );
        uCode.append( String.format(INDENT_FORMAT + "call main\n", "") );
        uCode.append( String.format(INDENT_FORMAT + "end\n", "") );

        completeUCode = uCode.toString();
        System.out.println(completeUCode);
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

    private boolean isAssignmentIncluded(MiniCParser.Var_declContext ctx) { return ctx.getChildCount() == 5; }

    private boolean isArrayDeclaration(MiniCParser.Var_declContext ctx) { return ctx.getChildCount() == 6; }

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
        varTable[1] = new LinkedHashMap<>();
        currentFunction.push( new Function(ctx.type_spec().getText(), ctx.IDENT().getText()) );
        funcTable.put(ctx.IDENT().getText(), currentFunction.peek());
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
            if(currentFunction.peek().isInt()) {
                exception.append(currentFunction.peek() + " : return expression doesn't appeared.\n");
            }
            uCode.append( String.format(INDENT_FORMAT + "ret\n", "") )
                    .append( String.format(INDENT_FORMAT + "end\n", "") );
        }

        currentBase--;
        currentFunction.pop();
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

    private boolean isArrayParam(MiniCParser.ParamContext ctx) { return ctx.getChildCount() == 4; }

    @Override
    public void enterParam(MiniCParser.ParamContext ctx) {
        super.enterParam(ctx);
        String paramName = ctx.IDENT().getText();

        if(isArrayParam(ctx)) {
            varTable[1].put(paramName, new Variable(currentBase, currentOffset, 0));
            currentFunction.peek().addParam(Type.ADDR);
        } else {
            varTable[1].put(paramName, new Variable(currentBase, currentOffset, 1));
            currentFunction.peek().addParam(Type.INT);
        }

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

    private boolean isOnlyUnaryOperation(MiniCParser.Expr_stmtContext ctx) {
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

        partialAST.pop();
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

        if(!partialAST.peek().equals(Type.INT))
            exception.append(ctx.expr().getText() + " : " + partialAST.peek() + " cannot be condition.\n");
        partialAST.pop();

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

        for ( MiniCParser.StmtContext stmtChild : ctx.stmt() )
            uCode.append( codeSet.get(stmtChild) );

        codeSet.put(ctx, uCode.toString());
    }

    private boolean isAssignmentIncluded(MiniCParser.Local_declContext ctx) { return ctx.getChildCount() == 5; }

    private boolean isArrayDeclaration(MiniCParser.Local_declContext ctx) { return ctx.getChildCount() == 6; }

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

    private boolean isElseIncluded(MiniCParser.If_stmtContext ctx) { return ctx.getChildCount() == 7; }

    @Override
    public void enterIf_stmt(MiniCParser.If_stmtContext ctx) {
        super.enterIf_stmt(ctx);
    }

    @Override
    public void exitIf_stmt(MiniCParser.If_stmtContext ctx) {
        super.exitIf_stmt(ctx);
        StringBuilder uCode = new StringBuilder();
        String condition = codeSet.get(ctx.expr());

        if (!partialAST.peek().equals(Type.INT))
            exception.append(ctx.expr().getText() + " : " + partialAST.peek() + " cannot be condition.\n");
        partialAST.pop();

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

    private boolean isValueReturn(MiniCParser.Return_stmtContext ctx) {
        return ctx.getChildCount() == 3;
    }

    @Override
    public void exitReturn_stmt(MiniCParser.Return_stmtContext ctx) {
        super.exitReturn_stmt(ctx);
        StringBuilder uCode = new StringBuilder();
        isReturnCalled = true;

        if (isValueReturn(ctx)) {
            if(currentFunction.peek().isVoid())
                exception.append(currentFunction.peek() + " : this function doesn't have return type.\n");
            if(!partialAST.peek().equals(Type.INT))
                exception.append(ctx.expr().getText() + " : this value is not int type but " + partialAST.peek() + " type.\n");
            partialAST.pop();
            uCode.append(codeSet.get(ctx.expr()))
                    .append( String.format(INDENT_FORMAT + "retv\n", "") );
        } else {
            if(currentFunction.peek().isInt())
                exception.append(currentFunction.peek() + " : this function needs to return value.\n");
            uCode.append( String.format(INDENT_FORMAT + "ret\n", "") );
        }

        codeSet.put(ctx, uCode.toString());
    }

    @Override
    public void enterExpr(MiniCParser.ExprContext ctx) {
        super.enterExpr(ctx);
        if (isFunctionCall(ctx)) {
            if ( !funcTable.containsKey(ctx.IDENT().getText()) )
                exception.append(ctx.IDENT().getText() + " : this function is undefined.\n");
            currentFunction.push(funcTable.get(ctx.IDENT().getText()));
        }
    }

    private boolean isBracketedOperand(MiniCParser.ExprContext ctx) {
        return ctx.IDENT() == null && ctx.getChildCount() == 3 && ctx.getChild(1) == ctx.expr(0);
    }

    private boolean isIDENT(MiniCParser.ExprContext ctx) {
        return ctx.getChild(0) == ctx.IDENT();
    }

    private boolean isLITERAL(MiniCParser.ExprContext ctx) {
        return ctx.getChild(0) == ctx.LITERAL();
    }

    private boolean isOperand(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 1;
    }

    private boolean isArrayOperand(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 4 && ctx.getChild(2) == ctx.expr(0);
    }

    private boolean isFunctionCall(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 4 && ctx.getChild(2) == ctx.args();
    }

    private boolean isUnaryOperation(MiniCParser.ExprContext ctx) {
        return ctx.getChildCount() == 2;
    }

    private boolean isBinaryOperation(MiniCParser.ExprContext ctx) {
        return ctx.IDENT() == null && ctx.getChildCount() == 3 && ctx.getChild(1) != ctx.expr(0);
    }

    private boolean isAssignment(MiniCParser.ExprContext ctx) {
        return ctx.IDENT() != null && ctx.getChildCount() == 3;
    }

    private boolean isArrayAssignment(MiniCParser.ExprContext ctx) {
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
                if (operand == null)
                    exception.append(ctx.getChild(0).getText() + " : this variable is undefined.\n");

                if (operand.isNeedToLoadAddr()) {
                    uCode.append(String.format(INDENT_FORMAT + "lda %d %d\n", "", operand.base, operand.offset));
                    partialAST.push(Type.ADDR);
                } else {
                    uCode.append(String.format(INDENT_FORMAT + "lod %d %d\n", "", operand.base, operand.offset));
                    partialAST.push(Type.INT);
                }
            } else if (isLITERAL(ctx)) {
                String operand = ctx.getChild(0).getText();
                uCode.append( String.format(INDENT_FORMAT + "ldc %s\n", "", operand) );
                partialAST.push(Type.INT);
            }
        }

        else if (isArrayOperand(ctx)) {
            Variable arrayId = varTable[1].get( ctx.IDENT().getText() );
            if (arrayId == null)
                arrayId = varTable[0].get( ctx.getChild(0).getText() );
            if (arrayId == null)
                exception.append(ctx.getChild(0).getText() + " : this variable is undefined.\n");
            if (!partialAST.peek().equals(Type.INT))
                exception.append(ctx.expr(0).getText() + " : this value must be int type but it's " + partialAST.peek() + " type.\n");
            partialAST.pop();

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

            partialAST.push(Type.INT);
        }

        else if (isFunctionCall(ctx)) {
            String funcName = ctx.IDENT().getText(),
                    args = codeSet.get(ctx.args());
            uCode.append( String.format(INDENT_FORMAT + "ldp\n", "") )
                    .append( args )
                    .append( String.format(INDENT_FORMAT + "call %s\n", "", funcName) );

            partialAST.push( currentFunction.peek().type );
            currentFunction.pop();
        }

        else if (isUnaryOperation(ctx)) {
            String op = ctx.getChild(0).getText();
            String operand = codeSet.get(ctx.expr(0));

            uCode.append(operand);
            for ( UnaryOperation u_op : UnaryOperation.values() ) {
                if (u_op.equals(op)) { //일항 연산자의 경우, ++, --의 경우는 출현과 동시에 실제 변수에 영향을 미침
                    uCode.append( String.format(INDENT_FORMAT + u_op, "") );
                    if (u_op.isNeededAssignment()) { //++, -- 일 경우, assign 후 스택에 다시 push
                        if (ctx.expr(0).IDENT() == null)
                            exception.append(ctx.expr(0) + " : this expression must be modifiable value.\n");

                        Variable id = varTable[1].get(ctx.expr(0).IDENT().getText());
                        if (id == null)
                            id = varTable[0].get( ctx.getChild(0).getText() );
                        if (id == null)
                            exception.append(ctx.getChild(0).getText() + " : this variable is undefined.\n");
                        if (!partialAST.peek().equals(Type.INT))
                            exception.append(ctx.getChild(0).getText() + " : this variable must be int type but it's " + partialAST.peek() +" type.\n");
                        partialAST.pop();

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
            partialAST.push(Type.INT);
        }

        else if (isBinaryOperation(ctx)) {
            String op = ctx.getChild(1).getText();
            String operand_1 = codeSet.get(ctx.expr(0)),
                   operand_2 = codeSet.get(ctx.expr(1));

            if (!partialAST.peek().equals(Type.INT))
                exception.append(ctx.expr(1).getText() + " : this value must be int type but it's " + partialAST.peek() + " type.\n");
            partialAST.pop();
            if (!partialAST.peek().equals(Type.INT))
                exception.append(ctx.expr(0).getText() + " : this value must be int type but it's " + partialAST.peek() + " type.\n");
            partialAST.pop();

            for ( BinaryOperation b_op : BinaryOperation.values() ) {
                if (b_op.equals(op)) {
                    uCode.append(operand_1)
                            .append(operand_2)
                            .append( String.format(INDENT_FORMAT + b_op, "") );
                    break;
                }
            }
            partialAST.push(Type.INT);
        }

        else if (isAssignment(ctx)) {
            Variable id = varTable[1].get( ctx.IDENT().getText() );
            if (id == null)
                id = varTable[0].get( ctx.getChild(0).getText() );
            if (id == null)
                exception.append(ctx.getChild(0).getText() + " : this variable is undefined.\n");
            if (!partialAST.peek().equals(Type.INT))
                exception.append(ctx.expr(0).getText() + " : this value must be int type but it's " + partialAST.peek() + " type.\n");
            partialAST.pop();

            String expr = codeSet.get(ctx.expr(0));
            uCode.append( expr )
                    .append( String.format(INDENT_FORMAT + "str %d %d\n", "", id.base, id.offset) );

            partialAST.push(Type.VOID);
        }

        else if (isArrayAssignment(ctx)) {
            Variable arrayId = varTable[1].get( ctx.IDENT().getText() );
            if (arrayId == null)
                arrayId = varTable[0].get( ctx.getChild(0).getText() );
            if (arrayId == null)
                exception.append(ctx.getChild(0).getText() + " : this variable is undefined.");
            if (!partialAST.peek().equals(Type.INT))
                exception.append(ctx.expr(1).getText() + " : this value must be int type but it's " + partialAST.peek() + " type.\n");
            partialAST.pop();
            if (!partialAST.peek().equals(Type.INT))
                exception.append(ctx.expr(0).getText() + " : this value must be int type but it's " + partialAST.peek() + " type.\n");
            partialAST.pop();

            String arrayNumber = codeSet.get(ctx.expr(0)),
                    expr = codeSet.get(ctx.expr(1));

            if (arrayId.isNeedToLoadAddr()) {
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
            partialAST.push(Type.VOID);
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

        if (currentFunction.peek().parameterSize() != ctx.expr().size())
            exception.append(ctx.getText() + " : arguments' size is unequal.\n");
        for (int i = currentFunction.peek().params.size() - 1; i >= 0; i--) {
            if(!currentFunction.peek().params.get(i).equals( partialAST.peek()) ) {
                exception.append(ctx.expr(i).getText() + " : this value must be " + currentFunction.peek().params.get(i) + " but it's " + partialAST.peek() + " type.\n");
            }
            partialAST.pop();
        }

        for ( MiniCParser.ExprContext exprChild : ctx.expr() )
            uCode.append( codeSet.get(exprChild) );

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
        private Variable(int base, int offset, int size) {
            this.base = base;
            this.offset = offset;
            this.size = size;
        }

        private boolean isArrayVariable() {
            return size != 1;
        }

        private boolean isNeedToLoadAddr() {
            return size > 1;
        }
    }

    class Function {
        private String name;
        private Type type;
        private LinkedList<Type> params;

        public Function(String type, String name) {
            if("int".equals(type))
                this.type = Type.INT;
            else if("void".equals(type))
                this.type = Type.VOID;
            params = new LinkedList<>();
            this.name = name;
        }

        public void addParam(Type type) {
            params.addLast(type);
        }

        public int parameterSize() { return params.size(); }

        public boolean isInt() { return type.equals(Type.INT); }

        public boolean isVoid() { return type.equals(Type.VOID); }

        @Override
        public String toString() { return name; }
    }

    enum Type {
        INT("int"),
        ADDR("address"),
        VOID("void");

        private final String typeName;
        Type(String typeName) { this.typeName = typeName; }
        @Override public String toString() { return typeName;}
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