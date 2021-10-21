declare namespace JS_TESTS {
    type Nullable<T> = T | null | undefined
    namespace foo {



        function invalid_args_name_sum(first$32$value: number, second$32$value: number): number;

        class A1 {
            constructor(first$32$value: number, second$46$value: number);
            readonly "first value": number;
            "second.value": number;
        }
        class A2 {
            constructor();
            "invalid:name": number;
        }
        class A3 {
            constructor();
            "invalid@name sum"(x: number, y: number): number;
            invalid_args_name_sum(first$32$value: number, second$32$value: number): number;
        }
        class A4 {
            constructor();
            static readonly Companion: {
                "@invalid+name@": number;
                "^)run.something.weird^("(): string;
            };
        }
    }
}