package net.lofers.scenario;

import java.lang.reflect.Proxy;
import javax.xml.parsers.DocumentBuilderFactory;
import zombie.characters.action.ActionContext;
import zombie.characters.action.conditions.CharacterVariableCondition;
import zombie.core.skinnedmodel.advancedanimation.*;

/** Reproduce the native transition's missing-versus-false distinction without a world. */
final class PedestrianConditionFixture {
    static void run() throws Exception {
        var variables = new AnimationVariableSource();
        var actor = (IAnimatable)Proxy.newProxyInstance(IAnimatable.class.getClassLoader(),
            new Class<?>[]{IAnimatable.class}, (proxy, method, args) -> {
                if (method.getName().equals("getVariable")) {
                    if (args[0] instanceof AnimationVariableHandle handle) return variables.getVariable(handle);
                    return variables.getVariable((String)args[0]);
                }
                if (method.getName().equals("getUID")) return "condition-fixture";
                throw new UnsupportedOperationException(method.getName());
            });
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        var node = document.createElement("isFalse"); node.setTextContent("bPathfind");
        var condition = new CharacterVariableCondition.Factory().create(node);
        var context = new ActionContext(actor);
        if (condition.passes(context, null)) throw new AssertionError("Missing path state treated as false");
        variables.setVariable("bPathfind", false);
        if (!condition.passes(context, null)) throw new AssertionError("Explicit false did not admit transition");
        variables.setVariable("bPathfind", true);
        if (condition.passes(context, null)) throw new AssertionError("Active pathfinding admitted direct-walk transition");
        System.out.println("Native pedestrian path-state condition regression passed");
    }
}
