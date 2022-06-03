/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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

import static com.oracle.truffle.js.builtins.TaintBuiltinsFactory.*;

public final class TaintBuiltins extends JSBuiltinsContainer.SwitchEnum<TaintBuiltins.Taint> {

    public static final JSBuiltinsContainer BUILTINS = new TaintBuiltins();
    private static final boolean DEFAULT_TAINT = true;

    TaintBuiltins() {
        super(JSRealm.TAINT_CLASS_NAME, TaintBuiltins.Taint.class);
    }

    public enum Taint implements BuiltinEnum<Taint> {
        addTaint,
        addTaintInRange,
        getTaint,
        getTaintAtIndex,
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
            case addTaintInRange:
                return JSAddTaintInRangeNodeGen.create(context, builtin, args().fixedArgs(4).createArgumentNodes(context));
            case getTaint:
                return JSGetTaintNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            case getTaintAtIndex:
                return GetTaintAtIndexNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
            case isTainted:
                return JSIsTaintedNodeGen.create(context, builtin, args().fixedArgs(1).createArgumentNodes(context));
            case removeTaint:
                return JSRemoveTaintNodeGen.create(context, builtin, args().fixedArgs(3).createArgumentNodes(context));
            default:
                return null; // see ConsoleBuiltins
        }
    }

    public abstract static class JSAddTaintNode extends JSBuiltinNode {

        public JSAddTaintNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        AbstractTruffleString addTaint(AbstractTruffleString value, Object taint,
                                             @Cached TSTaintNodes.AddTaintNode addTaintNode) {
            return addTaintNode.execute(value, JSGuards.isUndefined(taint) ? DEFAULT_TAINT : taint);
        }
    }

    public abstract static class JSAddTaintInRangeNode extends JSBuiltinNode {

        public JSAddTaintInRangeNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        AbstractTruffleString addTaint(AbstractTruffleString value, Object taint, int from, int to,
                                       @Cached TSTaintNodes.AddTaintInRangeNode addTaintInRangeNode) {
            try {
                return addTaintInRangeNode.execute(value, JSGuards.isUndefined(taint) ? DEFAULT_TAINT : taint, from, to);
            } catch (IndexOutOfBoundsException e) {
                throw Errors.createError("Failed to add taint due to some index being out of bounds", e);
            }
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

    public abstract static class GetTaintAtIndexNode extends JSBuiltinNode {

        public GetTaintAtIndexNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        Object getTaintAtIndex(AbstractTruffleString a, int index,
                               @Cached TSTaintNodes.GetTaintAtCodePointNode getTaintAtCodePointNode) {
            try {
                final Object taint = getTaintAtCodePointNode.execute(a, index);
                return taint == null ? Null.instance : taint;
            } catch (IndexOutOfBoundsException e) {
                throw Errors.createError("Failed to get taint due to index being out of bounds", e);
            }
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
