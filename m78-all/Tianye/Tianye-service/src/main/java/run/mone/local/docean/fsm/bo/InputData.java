package run.mone.local.docean.fsm.bo;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Builder;
import lombok.Data;

import java.util.Objects;

/**
 * @author goodjava@qq.com
 * @date 2024/3/1 14:13
 */
@Data
@Builder
public class InputData implements ItemData {

    //是否是原始input，而非coreSetting set进来的
    @Builder.Default
    private boolean originalInput = true;

    private int flowId;

    private String referenceName;

    private String name;

    //value reference imageReference batch imageReferenceBatch
    @Builder.Default
    private String type = "value";

    @Builder.Default
    private String valueType = "string";

    //和后边数据的操作符
    private String operator;

    private JsonElement value;

    private int flowId2;

    @Builder.Default
    private String type2 = "value";

    private String referenceName2;

    private JsonElement value2;

    //or and
    private String relationship;

    private String version;

    private String schema;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InputData inputData)) return false;

        if (flowId != inputData.flowId) return false;
        return Objects.equals(name, inputData.name);
    }

    @Override
    public int hashCode() {
        int result = flowId;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String getValueType() {
        return null;
    }

    public JsonElement getValue2() {
        return null==value2?new JsonPrimitive(""):value2;
    }

    public boolean isEmptyValue(){
        return (null==value || value.toString().equals("\"\""))?true:false;
    }

    public boolean isTypeReference(){
        return "reference".equals(this.type);
    }

    public boolean isTypeImage(){
        return "image".equals(this.type) || "imageReference".equals(this.type) || "imageReferenceBatch".equals(type);
    }

    public boolean isType2Reference(){
        return "reference".equals(this.type2);
    }

    public boolean isBatchType(){
        return "batch".equals(type) || "imageReferenceBatch".equals(type);
    }
}
