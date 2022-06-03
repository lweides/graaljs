function addTaint() {
    const foo = "foo";
    console.assert(!Taint.isTainted(foo), "Should not have been tainted");

    const tainted = Taint.addTaint(foo, "bar");
    console.assert(Taint.isTainted(tainted), "Should have been tainted");
    console.assert(!Taint.isTainted(foo), "Should not have been tainted");
}

function getTaint() {
    const foo = "foo";
    const taintArr = Taint.getTaint(foo);

    console.assert(taintArr.length === 3, "Should have same length as \"foo\"");
    assertTaintLabelsAreEqual([undefined, undefined, undefined], taintArr);

    const tainted = Taint.addTaint("foo", "bar");
    const taintedTaintArr = Taint.getTaint(tainted);
    console.assert(taintedTaintArr.length === 3, "Should have same length as \"foo\"");
    assertTaintLabelsAreEqual(["bar", "bar", "bar"], taintedTaintArr);
}

function addTaintInRange() {
    const foobar = "foobar";
    const tainted = Taint.addTaintInRange(foobar, true, 2, 5);
    const taintArr = Taint.getTaint(tainted);

    console.assert(Taint.isTainted(tainted), "Should have been tainted");
    assertTaintLabelsAreEqual(
        [null, null, true, true, true, null],
        taintArr
    );
}

function getTaintAtIndex() {
    const foo = "bar";
    const t0 = Taint.addTaintInRange(foo, 1, 0, 1);
    const t1 = Taint.addTaintInRange(t0, 2, 1, 2);
    const t2 = Taint.addTaintInRange(t1, 3, 2, 3);

    for (let i = 0; i < foo.length; i++) {
        console.assert(Taint.getTaintAtIndex(t2, i) === i + 1, "Invalid taint label");
    }
}

function removeTaint() {
    const foobar = Taint.addTaint("foobar", 1);
    const partiallyTainted = Taint.removeTaint(foobar, 2, 5);
    const taintArr = Taint.getTaint(partiallyTainted);

    console.assert(Taint.isTainted(partiallyTainted), "Should be tainted");

    assertTaintLabelsAreEqual(
        [1, 1, null, null, null, 1],
        taintArr
    );
}

function isTainted() {
    const empty = "";
    console.assert(!Taint.isTainted(empty), "Empty string cannot be tainted");

    const emptyTainted = Taint.addTaint(empty);
    console.assert(!Taint.isTainted(emptyTainted), "Empty string cannot be tainted");

    const tainted = Taint.addTaint("foo", "bar");
    console.assert(Taint.isTainted(tainted), "Should be tainted");
}


addTaint();
getTaint();
addTaintInRange();
getTaintAtIndex();
removeTaint();
isTainted();

function assertTaintLabelsAreEqual(expected, actual) {
    console.assert(expected.length === actual.length, "Lengths differ");
    for (let i = 0; i < expected.length; i++) {
        console.assert(expected[i] === actual[i], `Taint labels differ at index ${i}`);
    }
}