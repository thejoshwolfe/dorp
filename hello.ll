define i32 @main() {
  %result = call i32 @factorial(i32 5)
  ret i32 %result
}

; int factorial(int n) {
;   int result = n;
;   while (n > 1) {
;     n--;
;     result *= n;
;   }
;   return result;
; }
define private i32 @factorial(i32 %n) {
  ; (int n is modifiable)
  %n_ptr = alloca i32
  store i32 %n, i32* %n_ptr
  ; int result = n;
  %result_ptr = alloca i32
  store i32 %n, i32* %result_ptr
  br label %loop
loop:
  ; while (n > 1) {
  %value = load i32* %n_ptr
  %keep_going = icmp sgt i32 %value, 1
  br i1 %keep_going, label %iterate, label %return
iterate:
  ; n--;
  %next_value = sub i32 %value, 1
  store i32 %next_value, i32* %n_ptr
  ; result *= n;
  %result = load i32* %result_ptr
  %modified_result = mul i32 %next_value, %result
  store i32 %modified_result, i32* %result_ptr
  ; }
  br label %loop
return:
  ; return result;
  %retval = load i32* %result_ptr
  ret i32 %retval
}
