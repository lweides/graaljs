package com.oracle.truffle.js.builtins;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TSTaintNodes;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JSGuards;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.builtins.JSArrayObject;
import com.oracle.truffle.js.runtime.objects.Null;

import java.util.Arrays;

import static com.oracle.js.parser.ParserStrings.constant;
import static com.oracle.truffle.js.builtins.TaintBuiltinsFactory.*;

public final class TaintBuiltins extends JSBuiltinsContainer.SwitchEnum<TaintBuiltins.Taint> {

    public static final JSBuiltinsContainer BUILTINS = new TaintBuiltins();

    TaintBuiltins() {
        super(JSRealm.TAINT_CLASS_NAME, TaintBuiltins.Taint.class);
    }

    public enum Taint implements BuiltinEnum<Taint> {
        addTaint,
        getTaint,
        isTainted,
        removeTaint;

        @Override
        public int getLength() {
            return 0;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, Taint builtinEnum) {
        switch (builtinEnum) {
            case addTaint:
                return JSAddTaintNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
            case getTaint:
                return JSGetTaintNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            case isTainted:
                return JSIsTaintedNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            case removeTaint:
                return JSRemoveTaintNodeGen.create(context, builtin, args().fixedArgs(3).createArgumentNodes(context));
            default:
                return null; // see ConsoleBuiltins
        }
    }

    public abstract static class JSAddTaintNode extends JSBuiltinNode {

        // TODO replace default taint by source code location
        private static final TruffleString DEFAULT_TAINT = constant("DEFAULT_TAINT");

        public JSAddTaintNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        AbstractTruffleString addTaint(AbstractTruffleString value, Object taint,
                                             @Cached TSTaintNodes.AddTaintNode addTaintNode) {
            return addTaintNode.execute(value, JSGuards.isUndefined(taint) ? DEFAULT_TAINT : taint);
        }
    }

    public abstract static class JSIsTaintedNode extends JSBuiltinNode {

        public JSIsTaintedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        boolean isTainted(AbstractTruffleString a,
                          @Cached TSTaintNodes.IsTaintedNode isTaintedNode) {
            return isTaintedNode.execute(a);
        }
    }

    public abstract static class JSGetTaintNode extends JSBuiltinNode {

        public JSGetTaintNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(guards = "isTaintedNode.execute(a)")
        JSArrayObject getTaintTainted(AbstractTruffleString a,
                          @Cached TSTaintNodes.GetTaintNode getTaintNode,
                          @Cached TSTaintNodes.IsTaintedNode isTaintedNode) {
            final Object[] taint = mapNull(getTaintNode.execute(a));
            return JSArray.createZeroBasedObjectArray(getContext(), getRealm(), taint);
        }

        @Specialization(guards = "!isTaintedNode.execute(a)")
        JSArrayObject getTaintUntainted(TruffleString a,
                                        @Cached TSTaintNodes.IsTaintedNode isTaintedNode) {
            return JSArray.createEmpty(getContext(), getRealm(), Strings.length(a));
        }

        private static Object[] mapNull(Object[] arr) {
            final Object[] mapped = Arrays.copyOf(arr, arr.length);
            for (int i = 0; i < mapped.length; i++) {
                if (mapped[i] == null) {
                    mapped[i] = Null.instance;
                }
            }
            return mapped;
        }
    }

    public abstract static class JSRemoveTaintNode extends JSBuiltinNode {

        public JSRemoveTaintNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        AbstractTruffleString removeTaint(AbstractTruffleString a, int from, int to,
                          @Cached TSTaintNodes.RemoveTaintNode removeTaintNode) {
            try {
                return removeTaintNode.execute(a, from, to);
            } catch (IndexOutOfBoundsException e) {
                throw Errors.createError("Failed to remove taint due to index being out of bounds", e);
            }
        }
    }
}
