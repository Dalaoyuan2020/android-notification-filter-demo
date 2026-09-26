import com.example.notificationdemo.filter.DecisionEngine;
import com.example.notificationdemo.filter.DecisionEngine.Action;
import com.example.notificationdemo.filter.DecisionEngine.Input;
import com.example.notificationdemo.filter.DecisionEngine.Rules;

/** No Android runtime or testing-library dependency. Run with assertions implemented below. */
public final class DecisionEngineTest {
    private static int count;
    private static final Rules DEFAULT = new Rules("com.sina.weibo,com.example.notificationdemo.sender",
            "紧急,会议,重要,家人", "热搜,推荐,优惠,广告");

    public static void main(String[] args) {
        expect("explicit blocked phrase", Action.REMOVE, input("今日热搜", "查看全文"), DEFAULT);
        expect("title match", Action.REMOVE, input("广告", ""), DEFAULT);
        expect("body match", Action.REMOVE, input("新消息", "你的优惠券"), DEFAULT);
        expect("keep wins in same field", Action.KEEP, input("重要广告会议", ""), DEFAULT);
        expect("keep title wins over blocked body", Action.KEEP, input("家人", "推荐一款商品"), DEFAULT);
        expect("keep body wins over blocked title", Action.KEEP, input("热搜", "会议马上开始"), DEFAULT);
        expect("ordinary content stays", Action.KEEP, input("新消息", "今天天气不错"), DEFAULT);
        expect("empty payload stays", Action.KEEP, input(null, null), DEFAULT);
        expect("whitespace payload stays", Action.KEEP, input(" \t", "\n"), DEFAULT);
        expect("non-target app untouched", Action.SKIP,
                new Input("com.tencent.mm", "广告", "", false, true, false, "msg"), DEFAULT);
        expect("package matching exact", Action.SKIP,
                new Input("com.sina.weibo.evil", "广告", "", false, true, false, ""), DEFAULT);
        expect("persistent notification protected", Action.SKIP,
                new Input("com.sina.weibo", "广告", "", true, true, false, ""), DEFAULT);
        expect("unclearable notification protected", Action.SKIP,
                new Input("com.sina.weibo", "广告", "", false, false, false, ""), DEFAULT);
        expect("summary protected", Action.SKIP,
                new Input("com.sina.weibo", "广告", "", false, true, true, ""), DEFAULT);
        for (String category : new String[]{"call", "navigation", "alarm"}) {
            expect(category + " protected", Action.SKIP,
                    new Input("com.sina.weibo", "广告", "", false, true, false, category), DEFAULT);
        }
        expect("ordinary group child eligible", Action.REMOVE,
                new Input("com.sina.weibo", "广告", "", false, true, false, "msg"), DEFAULT);
        Rules mixed = new Rules(" com.sina.weibo \n com.example.notificationdemo.sender ",
                "VIP，重要\n紧急", " SALE,广告 \n推荐 ");
        expect("case insensitive blocked phrase", Action.REMOVE, input("Summer Sale", ""), mixed);
        expect("case insensitive preservation priority", Action.KEEP, input("sale", "vip customer"), mixed);
        expect("full-width comma accepted", Action.KEEP, input("推荐", "重要"), mixed);
        expect("empty block list disables removal", Action.KEEP, input("广告", ""),
                new Rules("com.sina.weibo", "", ",，\n"));
        expect("empty target list disables removal", Action.SKIP, input("广告", ""),
                new Rules("", "", "广告"));
        expect("missing rules safe", Action.KEEP, input("广告", ""), null);
        expect("missing notification safe", Action.KEEP, null, DEFAULT);
        expect("null configuration safe", Action.SKIP, input("广告", ""), new Rules(null, null, null));
        String longText = "广告" + new String(new char[5000]).replace('\0', '字') + "重要";
        expect("retention checked before log truncation", Action.KEEP, input("", longText), DEFAULT);
        if (DecisionEngine.parseList("广告,广告，\n推荐").size() != 2) {
            throw new AssertionError("lists must drop duplicates and empty entries");
        }
        count++;
        System.out.println("PASS: " + count + " conservative notification-rule checks");
    }

    private static Input input(String title, String text) {
        return new Input("com.sina.weibo", title, text, false, true, false, "msg");
    }
    private static void expect(String label, Action expected, Input input, Rules rules) {
        DecisionEngine.Result result = DecisionEngine.decide(input, rules);
        if (result.action != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + result.action + " / " + result.reason);
        }
        if (result.reason == null || result.reason.isEmpty()) throw new AssertionError(label + ": missing reason");
        count++;
    }
}
