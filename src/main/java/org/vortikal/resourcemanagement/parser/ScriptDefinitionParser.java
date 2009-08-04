package org.vortikal.resourcemanagement.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.runtime.tree.CommonTree;
import org.vortikal.resourcemanagement.ScriptDefinition;
import org.vortikal.resourcemanagement.ScriptDefinition.ScriptType;

public class ScriptDefinitionParser {

    public ScriptDefinition parseScriptDefinition(String propName, ScriptType scriptType,
            List<CommonTree> paramValues) {
        Object params = getScriptParams(scriptType, paramValues);
        ScriptDefinition sd = new ScriptDefinition(propName, scriptType, params);
        return sd;
    }

    private Object getScriptParams(ScriptType scriptType, List<CommonTree> paramValues) {
        if (ScriptType.AUTOCOMPLETE.equals(scriptType)) {
            return getAutoCompleteParams(paramValues);
        } else if (ScriptType.SHOWHIDE.equals(scriptType)) {
            return getShowHideParams(paramValues);
        }
        return null;
    }

    private Object getAutoCompleteParams(List<CommonTree> paramValues) {
        Map<String, String> params = new HashMap<String, String>();
        for (CommonTree param : paramValues) {
            params.put(param.getText(), param.getChild(0).getText());
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private Object getShowHideParams(List<CommonTree> paramValues) {
        Map<String, List<String>> params = new HashMap<String, List<String>>();
        for (CommonTree param : paramValues) {
            String trigger = param.getText();
            List<String> affectedProps = new ArrayList<String>();
            List<CommonTree> l = param.getChildren();
            for (CommonTree c : l) {
                affectedProps.add(c.getText());
            }
            params.put(trigger, affectedProps);
        }
        return params;
    }

}
