package com.catchmind.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WordBank {
    private static final List<String> WORDS = List.of(
        "사과", "바나나", "수박", "딸기", "포도", "파인애플", "고구마", "옥수수",
        "김치찌개", "떡볶이", "비빔밥", "붕어빵", "아이스크림", "햄버거", "피자", "라면",
        "강아지", "고양이", "코끼리", "기린", "펭귄", "호랑이", "다람쥐", "돌고래",
        "자동차", "비행기", "자전거", "소방차", "헬리콥터", "우주선", "잠수함", "기차",
        "컴퓨터", "스마트폰", "냉장고", "선풍기", "우산", "안경", "가위", "시계",
        "축구공", "농구공", "기타", "피아노", "카메라", "망원경", "무지개", "눈사람"
    );

    // 한 게임(12라운드)에서 중복 없이 사용할 셔플된 제시어 목록 반환
    public static List<String> pickWords(int count) {
        List<String> pool = new ArrayList<>(WORDS);
        Collections.shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
    }
}