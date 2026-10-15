package com.kj.stackchan.api;

import java.util.UUID;
import com.kj.stackchan.delegation.OperationReceiptService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companion/operations")
public class CompanionOperationController {
    private final OperationReceiptService receipts;
    public CompanionOperationController(OperationReceiptService receipts) { this.receipts=receipts; }
    @GetMapping("/{key}") public ResponseEntity<OperationReceiptService.Receipt> get(@PathVariable String key,
            @RequestParam OperationReceiptService.Action action,@RequestParam UUID deviceId,@RequestParam UUID roleId,
            @RequestParam(required=false) UUID resourceId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipts.get(deviceId,roleId,action,key,resourceId));
    }
}
