package com.tony.consul;

import jodd.util.StringUtil;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Test {

    public static void main(String[] args) {

        String uri = "http://172.18.293.11:8500/aaa/bbb/ccc/demo:dd";

        String last = StringUtil.split(uri, "http://")[1];
        String[] paths = StringUtil.split(last, ":");
        paths[0] = "";
        String collect = Arrays.stream(paths).collect(Collectors.joining(":"));

        System.out.println("http://localhost" + collect);
    }
}
