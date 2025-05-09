/*
 *  Copyright 2020 Xiaomi
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.xiaomi.youpin.docean.test.demo;

import com.xiaomi.youpin.docean.Ioc;
import com.xiaomi.youpin.docean.anno.Controller;
import com.xiaomi.youpin.docean.anno.RequestMapping;
import com.xiaomi.youpin.docean.anno.RequestParam;
import com.xiaomi.youpin.docean.common.Safe;
import com.xiaomi.youpin.docean.mvc.ContextHolder;
import com.xiaomi.youpin.docean.mvc.MvcContext;
import com.xiaomi.youpin.docean.mvc.MvcResult;
import com.xiaomi.youpin.docean.mvc.context.WebSocketContext;
import com.xiaomi.youpin.docean.test.anno.TAnno;
import com.xiaomi.youpin.docean.test.bo.M;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author goodjava@qq.com
 * @date 2020/6/21
 */
@Controller
@Slf4j
public class DemoController {

    @Resource
    private Ioc ioc;

    public void init() {
        log.info("init controller");
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            Safe.runAndLog(() -> WebSocketContext.ins().getChannelMap().forEach((k, v) -> {
                WebSocketContext.ins().sendMessage(k, "server_ping");
            }));
        }, 0, 5, TimeUnit.SECONDS);
    }

    @RequestMapping(path = "/test")
    public DemoVo test() {
        DemoVo vo = new DemoVo();
        vo.setId("1");
        vo.setName("test");
        return vo;
    }


    //测试mono
    @RequestMapping(path = "/mono")
    public Mono<String> testMono() {
        return Mono.create(sink->{
            sink.success("mono");
        });
    }

    @RequestMapping(path = "/flux")
    public Flux<String> testFlux() {
        return Flux.create(sink->{
            sink.next("a");
            sink.next("b");
            sink.complete();
        });
    }



//    @RequestMapping(path = "/ws")
    public String test(String req) {
        return "ws:" + req;
    }

    @RequestMapping(path = "/ws")
    public Flux<String> ws(String req) {
        return Flux.create(sink->{
            sink.next("a");
            sink.next("b");
            sink.complete();
        });
    }



    @RequestMapping(path = "/header")
    public DemoVo header(MvcContext context) {
        DemoVo vo = new DemoVo();
        vo.setId("1");
        vo.setName("test");
        context.getResHeaders().put("name", "zzy");
        return vo;
    }


    @SneakyThrows
    @RequestMapping(path = "/view")
    public String view() {
        return new String(Files.readAllBytes(Paths.get("/Users/dongzhenxing/Documents/Mi/Projects/mione/jcommon/docean/src/test/resources/html/upload.html")));
    }


    @SneakyThrows
    @RequestMapping(path = "/a/**")
    public String a() {
//        TimeUnit.SECONDS.sleep(3);
        return "a:" + Thread.currentThread().getName();
    }


    @RequestMapping(path = "/p")
    public M p(MvcContext c, M m) {
        log.info("{}", c.getHeaders());
        m.setName("zz");
        return m;
    }


    @RequestMapping(path = "/test2")
    public DemoVo test2(MvcContext context, DemoVo req) {
        log.info("{}", context);
        DemoVo vo = new DemoVo();
        vo.setId("1:" + req.getId());
        vo.setName("test2");
        return vo;
    }

    @RequestMapping(path = "/test3")
    public DemoVo test3(DemoVo req, DemoVo req2) {
        DemoVo vo = new DemoVo();
        vo.setId("1:" + req.getId() + ":" + req2.getId());
        vo.setName("test3");
        return vo;
    }

    @RequestMapping(path = "/test4", method = "get")
    public DemoVo test4(MvcContext context) {
        log.info("{}", context);
        DemoVo vo = new DemoVo();
        vo.setName("test4");
        return vo;
    }

    @RequestMapping(path = "/test5")
    public DemoVo test5(DemoVo req) {
        DemoVo vo = new DemoVo();
        vo.setId(req.getId());
        vo.setName("test5");
        return vo;
    }

    @RequestMapping(path = "/test6")
    public DemoVo test6(DemoVo req) {
        DemoVo vo = new DemoVo();
        vo.setId(req.getId());
        vo.setName("test6:" + ContextHolder.getContext().get());
        return vo;
    }


    /**
     * test 302 jump
     *
     * @return
     */
    @RequestMapping(path = "/302")
    public MvcResult<String> go302() {
        MvcResult mr = new MvcResult<String>();
        mr.setCode(302);
        mr.setData("http://www.baidu.com");
        return mr;
    }

    @RequestMapping(path = "/ping")
    public String ping(MvcContext context) {
        log.info("header:{}", context.getHeaders());
        log.info("sessionId:{}", context.session().getId());
        return "pong";
    }

    @TAnno
    @RequestMapping(path = "/testg", method = "get")
    public String testGet(@RequestParam("a") int a, @RequestParam("b") int b) {
        return String.valueOf(a + b);
    }

    @TAnno
    @RequestMapping(path = "/testv", method = "get")
    public String testV(@RequestParam("a") String a) {
        return a;
    }

    @RequestMapping(path = "/testpost")
    public String testPost(String b) {
        log.info("b={}", b);
        return b;
    }


    /**
     * test session
     *
     * @return
     */
    @RequestMapping(path = "/tests", method = "get")
    public String testSession(MvcContext context) {
        String name = String.valueOf(context.session().getAttribute("name"));
        return "session:" + name;
    }


    @RequestMapping(path = "/tests2", method = "get")
    public String testSession2(MvcContext context) {
        String name = String.valueOf(context.session().getAttribute("name"));
        return "session:" + name;
    }

    //Test the scenario where only a single parameter is passed, and it is of type String.
    @RequestMapping(path = "/string")
    public String string(String str) {
        return str;
    }


    public void destory() {
        log.info("destory controller");
    }

}
