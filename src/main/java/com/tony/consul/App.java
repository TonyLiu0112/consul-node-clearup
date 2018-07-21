package com.tony.consul;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jodd.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class App {

    private static OkHttpClient client = new OkHttpClient();
    private final static String LOCALHOST = "localhost";
    private final static String PROTOCOL = "http://";
    private final static Integer PORT = 8500;
    private final static String SERVER_REGISTER = "/v1/agent/service/deregister/";
    private final static String CHECK = "/v1/agent/checks";
    private final static String MEMBERS = "/v1/agent/members";

    private static Boolean isLocal = false;

    public static void main(String[] args) throws IOException {
        System.out.println("请输入需要清理的任意consul节点, 例如: http://localhost:8500");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();
        if (input.contains("localhost"))
            isLocal = true;
        cleanup(input);
    }

    private static List<Members> members(String uri) throws IOException {
        String members = RpcOps.get(uri + MEMBERS);
        return JSON.parseArray(members, Members.class);
    }

    private static List<Checks> checks(String uri) throws IOException {
        List<Checks> checks = new ArrayList<>();
        String res = RpcOps.get(uri + CHECK);
        JSONObject jsonObject = JSON.parseObject(res);
        jsonObject.forEach((k, v) -> {
            Checks cks = JSON.parseObject(v.toString(), Checks.class);
            checks.add(cks);
        });
        return checks;
    }

    private static void deregister(String uri, Checks check) throws IOException {
        String status = check.Status;
        if (status == null || "".equalsIgnoreCase(status) || "passing".equalsIgnoreCase(status)) {
            System.out.println("节点[" + check.ServiceID + "]状态正常, 跳过...");
            return;
        }
        RpcOps.put(uri + SERVER_REGISTER + check.ServiceID);
        System.out.println("service清理: 节点[" + uri + "][" + check.ServiceID + "]清理完成");
    }

    private static void cleanup(String uri) throws IOException {
        System.out.println("Begin to cleanup...");
        List<Members> members = members(uri);
        if (members == null || members.size() == 0) {
            System.out.println(uri + " -> 未找到可用consul节点");
            return;
        }
        members.forEach(member -> {
            try {
                String membersUri = RpcOps.setPort(member.Addr);
                List<Checks> checks = checks(membersUri);
                if (checks == null || checks.size() == 0)
                    return;
                checks.forEach(check -> {
                    try {
                        deregister(membersUri, check);
                    } catch (IOException e) {
                        System.err.println("实例" + membersUri + " " + check.ServiceID + "清理失败. " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                System.err.println("节点" + member.Addr + "清理失败. " + e.getMessage());
            }
        });
        System.out.println("success to cleanup!");
    }

    private static class RpcOps {

        private static String get(String uri) throws IOException {
            uri = wrapper(uri);
            Request request = new Request.Builder()
                    .url(uri)
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                assert response.code() == 200;
                assert response.body() != null;
                return response.body().string();
            }
        }

        static void put(String uri) throws IOException {
            uri = wrapper(uri);
            Request request = new Request.Builder()
                    .url(uri)
                    .method("PUT", new FormBody.Builder().build())
                    .build();
            try (Response response = client.newCall(request).execute()) {
                assert response.code() == 200;
                assert response.body() != null;
            }
        }

        private static String wrapper(String uri) {
            if (!uri.startsWith(PROTOCOL))
                uri = PROTOCOL + uri;
            if (isLocal) {
                String last = StringUtil.split(uri, "http://")[1];
                String[] paths = StringUtil.split(last, ":");
                paths[0] = "";
                String collect = Arrays.stream(paths).collect(Collectors.joining(":"));
                uri = PROTOCOL + LOCALHOST + collect;
            }
            return uri;
        }

        private static String setPort(String addr) {
            return addr + ":" + PORT;
        }


    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    private static class Members {
        private String Name;
        private String Addr;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    private static class Checks {
        private String ServiceID;
        private String Status;
        private String CheckID;
        private String Node;
    }
}
