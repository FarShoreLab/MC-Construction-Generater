package org.mcsettlement.llm;

import org.mcsettlement.planner.SettlementPlanner;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.preset.PlannedBuilding;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;

/** Loopback transport fixture only. No external service or paid model is contacted. */
public final class BridgeRegressionMain {
    private static int passed;
    private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private static void pass(String name){passed++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception {
        var map=new HeightfieldMap(0,0,64,64);for(int x=0;x<64;x++)for(int z=0;z<64;z++)map.setSurfaceY(x,z,64);
        String json="{\"targetPlotCount\":3,\"landmarkPlacement\":\"center\",\"requirements\":[{\"id\":\"homes\",\"purpose\":\"residential\",\"count\":2,\"minWidth\":10,\"maxWidth\":10},{\"id\":\"forge\",\"purpose\":\"workshop\",\"count\":1}]}";
        PlanningIntent intent=PlanningIntent.fromJson(json);var req=intent.toPlanRequest(88);
        check(req.seed==88&&req.landmarkPlacement.equals("center")&&req.requirements.size()==2&&req.requirements.getFirst().minWidth==10,"Intent fields lost");pass("intent_to_request_preserves_requirements");
        PlanningIntent offline=HeuristicFallbackBridge.generateIntent(map,"村庄需要铁匠铺和酒馆，不要让所有房子都是铁匠铺");
        check(offline.requirements.stream().filter(q->q.purpose.equals("workshop")).count()==1,"Global prompt applied to every plot");
        check(offline.requirements.stream().anyMatch(q->q.purpose.equals("residential")),"Housing lost");pass("offline_keywords_are_per_purpose");
        var config=new LlmStrategyManager.LlmConfig();config.preferLocalAgy=false;
        check(LlmStrategyManager.resolveIntent(map,"村庄",config).modelCalls==0,"Offline mode called model");pass("offline_manager_zero_model_calls");
        AtomicInteger requests=new AtomicInteger();AtomicReference<String> body=new AtomicReference<>("");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            requests.incrementAndGet();body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            var message=new com.google.gson.JsonObject();message.addProperty("content",json);
            var choice=new com.google.gson.JsonObject();choice.add("message",message);
            var choices=new com.google.gson.JsonArray();choices.add(choice);
            var response=new com.google.gson.JsonObject();response.add("choices",choices);
            byte[] bytes=response.toString().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            config.apiEndpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";config.apiKey="local-fixture-not-a-real-key";
            var remote=LlmStrategyManager.resolveIntent(map,"two houses and a forge",config);
            check(requests.get()==1&&remote.modelCalls==1&&remote.requirements.size()==2,"Fixture request count or parsing");
            var messages=com.google.gson.JsonParser.parseString(body.get()).getAsJsonObject().getAsJsonArray("messages");
            String payload=messages.get(0).getAsJsonObject().get("content").getAsString()+messages.get(1).getAsJsonObject().get("content").getAsString();
            check(payload.contains("minY=64")&&payload.contains("protectedCells=")&&payload.contains("maxWaterDistance"),"Insufficient terrain/schema payload");pass("single_loopback_intent_and_rich_schema");
            var plan=SettlementPlanner.plan(map,remote.toPlanRequest(42));check(plan.status.equals("COMPLETE"),"Bridge plan failed");
            for(var p:plan.plots) {
                var model=AiBuildingArchitect.designBuildingForPlot(p,"medieval_rustic","全部关键词包含铁匠铺",config);
                check(model.buildingName.equals(p.builder.presetId),"Global prompt overrode locked purpose");
                check(model.sizeX==p.builder.footprintSize[0]&&model.sizeZ==p.builder.footprintSize[1],"Clipped preset");
                var grid=PlannedBuilding.grid(p,"medieval_rustic");
                check(java.util.Arrays.deepEquals(model.blocks,grid),"Architect and constructor disagree");
            }
            check(requests.get()==1,"Per-building network calls occurred");pass("locked_buildings_no_extra_calls_or_prompt_override");
            var bad=PlanningIR.fromJson(plan.toJson(false)).plots.getFirst();bad.builder.footprintSize[0]++;
            boolean thrown=false;try{AiBuildingArchitect.designBuildingForPlot(bad,"medieval_rustic","",config);}catch(IllegalArgumentException expected){thrown=true;}
            check(thrown,"Dimension mismatch silently accepted");pass("mismatched_locked_dimensions_fail_closed");
        } finally {server.stop(0);}
        var invalid=PlanningIntent.fromJson("{\"roadWidth\":1000,\"targetPlotCount\":1000}");
        check(SettlementPlanner.plan(map,invalid.toPlanRequest(42)).status.equals("INVALID_REQUEST"),"Out-of-bound model requirements clamped silently");pass("untrusted_numeric_requirements_rejected");
        var a=new PlanningIntent();a.targetPlotCount=1;a.landmarkPlacement="center";
        var b=new PlanningIntent();b.targetPlotCount=1;b.landmarkPlacement="ridge";
        for(int x=0;x<64;x++)for(int z=0;z<64;z++)map.setSurfaceY(x,z,64+x/16);map.computeSlopes();
        var pa=SettlementPlanner.plan(map,a.toPlanRequest(42));var pb=SettlementPlanner.plan(map,b.toPlanRequest(42));
        check(!pa.plots.isEmpty()&&!pb.plots.isEmpty(),"Landmark fixture failed");
        check(!new com.google.gson.Gson().toJson(pa.plots.getFirst().polygon2D).equals(new com.google.gson.Gson().toJson(pb.plots.getFirst().polygon2D)),"Landmark preference has no geometric effect");pass("landmark_field_reaches_geometric_search");
        System.out.println("RESULT "+passed+" bridge checks passed; external model calls=0 (one loopback fixture request)");
    }
}
