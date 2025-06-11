package org.reference;

import java.util.HashMap;
import java.util.Map;

public class OpcodeMapper {
    private static final Map<Integer, String> opcodeToMethodInstruction = new HashMap<>();
    private static final Map<Integer, String> opcodeToFieldInstruction = new HashMap<>();

    static {
        opcodeToFieldInstruction.put(178, "GETSTATIC");
        opcodeToFieldInstruction.put(179, "PUTSTATIC");
        opcodeToFieldInstruction.put(180, "GETFIELD");
        opcodeToFieldInstruction.put(181, "PUTFIELD");
        opcodeToMethodInstruction.put(182, "INVOKEVIRTUAL");
        opcodeToMethodInstruction.put(183, "INVOKESPECIAL");
        opcodeToMethodInstruction.put(184, "INVOKESTATIC");
        opcodeToMethodInstruction.put(185, "INVOKEINTERFACE");
      //  opcodeToMethodInstruction.put(186, "INVOKEDYNAMIC"); TODO check if we need this I could not get the method name or a class for this type
    }

    public static String getMethodOpcodeInstruction(int opcode) {
        return opcodeToMethodInstruction.getOrDefault(opcode, null);
    }

    public static String getFieldOpcodeInstruction(int opcode) {
        return opcodeToFieldInstruction.getOrDefault(opcode, null);
    }
}
