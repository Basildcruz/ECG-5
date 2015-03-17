package com.babykeeper.ecg.bean;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.app.Application;

public class MyApp extends Application {

	public static MyApp mInstance;
	private List<Integer> list; // 存储原始数据

	@Override
	public void onCreate() {
		super.onCreate();
		mInstance = this;
		list = Collections.synchronizedList(new LinkedList<Integer>());// 初始化ArrayList
	}

	public static MyApp getInstance() {
		return mInstance;
	}

	public void addValue(int value) {
		list.add(value);
	}

	public void clearValue() {
		list.clear();
	}

	public int getValue() {
		int value = list.get(0);
		list.remove(0);
		return value;
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}

	public int getSize() {
		return list.size();
	}

	// 获取ecg数组数据
	public int[] getECG() {

		int[] ecg = new int[list.size()];
		for (int i = 0; i < list.size(); i++) {
			ecg[i] = list.get(i).intValue();
		}
		return ecg;
	}
}
