支持大多数kibana语法,只需要写语法即可生成SearchRequest去查询，具体可看单元测试

支持多种语法,使用括号可以支持优先级
需要需要新加语法或者减少语法，可以直接修改g4包下[EsQuery.g4]文件，然后在idea中
右击 Generate Antlr Recongnizer重新生成生成语法类（在query包下）
直接使用[EsQueryUtils.java]使用

表达式一： 变量 运算符 值
变量: 非数字开头的数字、字母、下划线组合
运算符：

":"  ：等于
"!="  ：不等于
">"   :大于
"<"   ：小于
"<="  ：小于等于
">="  ：大于等于
"=~"  : 正则
"!~"  : 正则取反
"IN"  ：包含在
"NOT_IN"  ：不包含在
"CONTAINS"
"NOTCONTAINS"

a>=2022-07-11 16:00:00.000 AND b<=2022-07-11 17:00:00.000
包含关系 name IN ["张三","李四","王五"]

":" "!=" ">" "<" "<=" ">=" "IN" "NOT_IN" "EXIST" "NOT_EXIST" 以及逻辑AND OR NOT

聚合效果后期支持